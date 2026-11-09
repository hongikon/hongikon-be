package com.hongmap.hongmapbackend.ask;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 운영진 확인 안내(ask_operator_notes)를 질문에 맞춰 고른다(11-06). 안내는 많아야 수백 개라 서버 메모리에 두고,
 * 관리자가 고치면 바로 다시 읽는다(서버 한 대).
 * <ul>
 *   <li>낱말: 질문과 안내 질문의 한글 2글자 묶음 겹침(Jaccard). 임베딩 서버가 없어도 된다.</li>
 *   <li>뜻(임베딩 서버가 있으면): 질문 벡터와 안내 질문 벡터의 내적. 안내 벡터는 처음 쓸 때 한 번 만들어 둔다.</li>
 * </ul>
 * 둘 중 하나라도 기준을 넘는 것만, 점수 순 최대 limit 개를 page:note-{id} 로 돌려준다.
 */
@Slf4j
@Component
public class AskOperatorNotes {

    public static final String PREFIX = AskRetriever.PAGE_PREFIX + "note-";
    static final double LEXICAL_MIN = 0.34;
    static final double SEMANTIC_MIN = 0.72;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AskOperatorNoteRepository repository;
    private final AskEmbedClient embedClient;
    private volatile List<AskOperatorNote> active;
    private final Map<Long, float[]> vectors = new ConcurrentHashMap<>();

    public AskOperatorNotes(AskOperatorNoteRepository repository, AskEmbedClient embedClient) {
        this.repository = repository;
        this.embedClient = embedClient;
    }

    /** 관리자가 안내를 더하거나 고쳤을 때 부른다. 트랜잭션 안이면 커밋된 뒤에 비운다(커밋 전 옛 값을 다시 담지 않게). */
    public void reload() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            clear();
                        }
                    });
        }
        clear();
    }

    private void clear() {
        active = null;
        vectors.clear();
    }

    private List<AskOperatorNote> active() {
        List<AskOperatorNote> current = active;
        if (current == null) {
            try {
                current = List.copyOf(repository.findByActiveTrue());
            } catch (RuntimeException e) {
                log.warn("운영진 확인 안내를 읽지 못함: {}", e.toString());
                current = List.of();
            }
            active = current;
        }
        return current;
    }

    /** @param queryVector 질문 임베딩(RAG 가 켜져 있으면 이미 만든 것, 없으면 null — 낱말로만 고른다) */
    public List<String> match(String question, float[] queryVector, int limit) {
        List<AskOperatorNote> notes = active();
        if (notes.isEmpty() || question == null || question.isBlank() || limit <= 0) {
            return List.of();
        }
        Set<String> q = grams(question);
        record Scored(AskOperatorNote note, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (AskOperatorNote note : notes) {
            double lexical = jaccard(q, grams(note.getQuestion()));
            double semantic = queryVector == null ? 0 : dot(queryVector, vector(note));
            if (lexical >= LEXICAL_MIN || semantic >= SEMANTIC_MIN) {
                scored.add(new Scored(note, Math.max(lexical, semantic)));
            }
        }
        return scored.stream().sorted(Comparator.comparingDouble(Scored::score).reversed()).limit(limit)
                .map(s -> PREFIX + s.note().getId()).toList();
    }

    /** page:note-{id} → 답 쓰기에 넘길 쪽(없거나 꺼졌으면 null). */
    public AskRetriever.Page page(String id) {
        if (id == null || !id.startsWith(PREFIX)) {
            return null;
        }
        long noteId;
        try {
            noteId = Long.parseLong(id.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
        for (AskOperatorNote note : active()) {
            if (note.getId() == noteId) {
                String date = note.getUpdatedAt() == null ? "" : " · " + DATE.format(note.getUpdatedAt());
                return new AskRetriever.Page("운영진 확인 안내" + date, "운영진", null,
                        "질문: " + note.getQuestion() + "\n답: " + note.getAnswer());
            }
        }
        return null;
    }

    private float[] vector(AskOperatorNote note) {
        float[] cached = vectors.get(note.getId());
        if (cached != null) {
            return cached;
        }
        if (!embedClient.configured()) {
            return null;
        }
        try {
            float[] made = embedClient.embed(note.getQuestion());
            vectors.put(note.getId(), made);
            return made;
        } catch (RuntimeException e) {
            log.warn("운영진 확인 안내 임베딩 실패: {}", e.toString());
            return null;
        }
    }

    static Set<String> grams(String text) {
        String compact = text == null ? "" : text.replaceAll("[^가-힣A-Za-z0-9]", "").toLowerCase();
        Set<String> out = new HashSet<>();
        for (int i = 0; i + 1 < compact.length(); i++) {
            out.add(compact.substring(i, i + 2));
        }
        return out;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int both = 0;
        for (String g : a) {
            if (b.contains(g)) {
                both++;
            }
        }
        return (double) both / (a.size() + b.size() - both);
    }

    private static double dot(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
