package com.hongmap.hongmapbackend.ask;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 물어보기 RAG 검색(docs/ask-embedding-plan.md). 안내 원문을 문단으로 자른 색인(ask/rag-chunks.json·rag-vectors.bin — 맥미니
 * tools/embed-server/build_index.py 가 만든다)에서 질문과 뜻이 가까운 문단(bge-m3)과 낱말이 겹치는 문단(BM25, 한글 2글자 단위)을
 * 찾아 순위를 합친다(RRF). 손으로 적은 낱말 목록(앱 낱말 찾기 hint·카탈로그 꼬리표) 대신 이것으로 안내 후보를 고른다(10-28 운영진 결정).
 *
 * ask.rag.enabled=false 이거나 색인이 없거나 색인 version 이 카탈로그와 다르면 꺼진다({@link #enabled()} false — 지금 방식 그대로).
 * 켜져 있는데 임베딩 서버가 실패하면 {@link AskUnavailableException}(낱말 찾기로 대신하지 않는다).
 */
@Slf4j
@Component
public class AskRetriever {

    static final String CHUNKS_RESOURCE = "ask/rag-chunks.json";
    static final String VECTORS_RESOURCE = "ask/rag-vectors.bin";
    /** RRF 상수(흔히 쓰는 60). 10-28 실험: bge-m3 + BM25 혼합이 기준 정답표 사실 포함 26/26. */
    static final int RRF_K = 60;
    static final double BM25_K1 = 1.5;
    static final double BM25_B = 0.75;

    private final AskEmbedClient embedClient;
    private final List<Chunk> chunks;
    private final float[][] vectors;
    private final Map<String, int[]> postings;
    private final int[] lengths;
    private final double avgLength;
    private final boolean enabled;

    @Autowired
    public AskRetriever(AskCatalog catalog, AskEmbedClient embedClient, JsonMapper jsonMapper,
                        @Value("${ask.rag.enabled:false}") boolean wanted) throws IOException {
        this(embedClient, wanted ? load(catalog, jsonMapper) : null, wanted);
    }

    AskRetriever(AskEmbedClient embedClient, Index index, boolean wanted) {
        this.embedClient = embedClient;
        this.enabled = wanted && index != null && embedClient.configured();
        this.chunks = index == null ? List.of() : index.chunks();
        this.vectors = index == null ? new float[0][] : index.vectors();
        Map<String, List<Integer>> grams = new HashMap<>();
        this.lengths = new int[chunks.size()];
        long total = 0;
        for (int i = 0; i < chunks.size(); i++) {
            List<String> tokens = bigrams(chunks.get(i).text());
            lengths[i] = tokens.size();
            total += tokens.size();
            for (String token : tokens) {
                grams.computeIfAbsent(token, k -> new ArrayList<>()).add(i);
            }
        }
        this.avgLength = chunks.isEmpty() ? 1 : (double) total / chunks.size();
        Map<String, int[]> packed = new HashMap<>();
        grams.forEach((gram, list) -> packed.put(gram, list.stream().mapToInt(Integer::intValue).toArray()));
        this.postings = packed;
        if (wanted && !enabled) {
            log.warn("물어보기 RAG 를 켜려 했지만 색인({}) 또는 임베딩 서버 키(ask.rag.embed-key)가 없어 지금 방식(카탈로그 고르기)으로 둔다.", CHUNKS_RESOURCE);
        } else if (enabled) {
            log.info("물어보기 RAG 켜짐: 문단 {}개", chunks.size());
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** 질문과 가까운 안내 id(문단 순위를 안내로 묶어 중복 없이) 최대 limit 개. */
    public List<String> nodes(String query, int limit) {
        Set<String> out = new LinkedHashSet<>();
        for (Hit hit : chunks(query, limit * 4)) {
            out.add(hit.nodeId());
            if (out.size() >= limit) {
                break;
            }
        }
        return List.copyOf(out);
    }

    /** 질문과 가까운 문단 최대 limit 개(RRF 점수 높은 순). */
    public List<Hit> chunks(String query, int limit) {
        if (!enabled || query == null || query.isBlank()) {
            return List.of();
        }
        float[] q = embedClient.embed(query);
        int n = chunks.size();
        double[] dense = new double[n];
        for (int i = 0; i < n; i++) {
            float[] v = vectors[i];
            double dot = 0;
            for (int d = 0; d < v.length && d < q.length; d++) {
                dot += v[d] * q[d];
            }
            dense[i] = dot;
        }
        double[] fused = new double[n];
        addRanks(fused, dense);
        addRanks(fused, bm25(query));
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(fused[b], fused[a]));
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, n); i++) {
            Chunk chunk = chunks.get(order[i]);
            hits.add(new Hit(chunk.id(), chunk.text(), fused[order[i]]));
        }
        return hits;
    }

    double[] bm25(String query) {
        double[] scores = new double[chunks.size()];
        int n = chunks.size();
        for (String gram : bigrams(query)) {
            int[] docs = postings.get(gram);
            if (docs == null) {
                continue;
            }
            Map<Integer, Integer> tf = new HashMap<>();
            for (int doc : docs) {
                tf.merge(doc, 1, Integer::sum);
            }
            double idf = Math.log(1 + (n - tf.size() + 0.5) / (tf.size() + 0.5));
            for (Map.Entry<Integer, Integer> entry : tf.entrySet()) {
                double f = entry.getValue();
                double norm = BM25_K1 * (1 - BM25_B + BM25_B * lengths[entry.getKey()] / avgLength);
                scores[entry.getKey()] += idf * f * (BM25_K1 + 1) / (f + norm);
            }
        }
        return scores;
    }

    /** 점수 순위로 1/(K+순위)를 더한다(RRF). 점수 0(낱말이 하나도 안 겹침)은 더하지 않는다. */
    private static void addRanks(double[] fused, double[] scores) {
        Integer[] order = new Integer[scores.length];
        for (int i = 0; i < scores.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(scores[b], scores[a]));
        for (int rank = 0; rank < order.length; rank++) {
            if (scores[order[rank]] > 0) {
                fused[order[rank]] += 1.0 / (RRF_K + rank + 1);
            }
        }
    }

    /** 공백을 지우고 소문자로 바꾼 뒤 2글자씩(색인 만들기·실험과 같은 규칙). */
    static List<String> bigrams(String text) {
        String s = text.replaceAll("\\s+", "").toLowerCase();
        List<String> out = new ArrayList<>(Math.max(0, s.length() - 1));
        for (int i = 0; i + 2 <= s.length(); i++) {
            out.add(s.substring(i, i + 2));
        }
        return out;
    }

    private static Index load(AskCatalog catalog, JsonMapper jsonMapper) throws IOException {
        ClassPathResource chunksFile = new ClassPathResource(CHUNKS_RESOURCE);
        ClassPathResource vectorsFile = new ClassPathResource(VECTORS_RESOURCE);
        if (!chunksFile.exists() || !vectorsFile.exists()) {
            return null;
        }
        ChunksFile file;
        try (InputStream in = chunksFile.getInputStream()) {
            file = jsonMapper.readValue(in, ChunksFile.class);
        }
        if (!catalog.version().equals(file.version())) {
            log.warn("물어보기 RAG 색인 version {} 이 카탈로그 {} 와 달라 쓰지 않는다 — build_index.py 를 다시 돌린다.", file.version(), catalog.version());
            return null;
        }
        byte[] bytes;
        try (InputStream in = vectorsFile.getInputStream()) {
            bytes = in.readAllBytes();
        }
        int count = file.chunks().size();
        if (bytes.length != (long) count * file.dim() * 2) {
            throw new IllegalStateException("rag-vectors.bin 크기가 문단 수 × 차원과 다르다.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[][] vectors = new float[count][file.dim()];
        for (int i = 0; i < count; i++) {
            for (int d = 0; d < file.dim(); d++) {
                vectors[i][d] = halfToFloat(buffer.getShort());
            }
        }
        return new Index(file.chunks(), vectors);
    }

    /** IEEE 754 반정밀도 → float(Java 17 에는 Float.float16ToFloat 가 없다). */
    static float halfToFloat(short half) {
        int h = half & 0xffff;
        int sign = (h >>> 15) << 31;
        int exp = (h >>> 10) & 0x1f;
        int mant = h & 0x3ff;
        if (exp == 0) {
            return (sign == 0 ? 1 : -1) * mant * (float) Math.pow(2, -24);
        }
        if (exp == 31) {
            return Float.intBitsToFloat(sign | 0x7f800000 | (mant << 13));
        }
        return Float.intBitsToFloat(sign | ((exp - 15 + 127) << 23) | (mant << 13));
    }

    record ChunksFile(String version, String model, int dim, List<Chunk> chunks) {
    }

    record Chunk(String id, String text) {
    }

    record Index(List<Chunk> chunks, float[][] vectors) {
    }

    public record Hit(String nodeId, String text, double score) {
    }
}
