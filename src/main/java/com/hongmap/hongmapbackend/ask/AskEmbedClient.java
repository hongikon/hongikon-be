package com.hongmap.hongmapbackend.ask;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 맥미니 임베딩 서버(hongikon-be/tools/embed-server) 호출 — 질문을 bge-m3 벡터로 바꾼다(docs/ask-embedding-plan.md).
 * 운영 서버와는 사설 터널 주소로만 잇는다. 질문 글은 이 서버 밖으로 나가지 않는다(외부 임베딩 업체를 쓰지 않는다).
 * 실패(꺼짐·키·시간 초과)면 {@link AskUnavailableException} — 앱은 낱말 찾기로 답하지 않고 실패를 알린다.
 */
@Component
public class AskEmbedClient {

    static final String KEY_HEADER = "X-Embed-Key";

    private final HttpClient http;
    private final URI embedUri;
    private final String key;
    private final Duration timeout;
    private final JsonMapper jsonMapper;

    public AskEmbedClient(JsonMapper jsonMapper,
                          @Value("${ask.rag.embed-url:http://127.0.0.1:8090}") String baseUrl,
                          @Value("${ask.rag.embed-key:}") String key,
                          @Value("${ask.rag.embed-timeout-ms:5000}") long timeoutMs) {
        this.jsonMapper = jsonMapper;
        this.embedUri = URI.create(baseUrl.replaceAll("/+$", "") + "/embed");
        this.key = key == null ? "" : key;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public boolean configured() {
        return !key.isBlank();
    }

    /** 문장 하나 → 길이 1로 맞춘 벡터. */
    public float[] embed(String text) {
        if (!configured()) {
            throw new AskUnavailableException("임베딩 서버 키가 없다(ask.rag.embed-key)");
        }
        try {
            String body = jsonMapper.writeValueAsString(Map.of("texts", List.of(text)));
            HttpRequest request = HttpRequest.newBuilder(embedUri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header(KEY_HEADER, key)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new AskUnavailableException("임베딩 서버 응답 " + response.statusCode());
            }
            JsonNode vector = jsonMapper.readTree(response.body()).path("vectors").path(0);
            float[] out = new float[vector.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = (float) vector.get(i).asDouble();
            }
            if (out.length == 0) {
                throw new AskUnavailableException("임베딩 서버가 빈 벡터를 돌려줬다");
            }
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AskUnavailableException("임베딩 서버 호출이 끊겼다");
        } catch (AskUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AskUnavailableException("임베딩 서버 호출 실패: " + e.getClass().getSimpleName());
        }
    }
}
