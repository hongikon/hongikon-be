package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.CrawlerProperties;
import lombok.RequiredArgsConstructor;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 재시도 + 타임아웃 + 요청 간 딜레이를 붙인 Jsoup 요청 래퍼.
 * hongmap(프론트) scripts/crawler/http.mjs의 fetchText/politeDelay에 대응.
 */
@Component
@RequiredArgsConstructor
public class CrawlerHttpClient {

    private final CrawlerProperties properties;

    /**
     * URL 하나를 문서로 가져온다.
     * 5xx/네트워크 오류는 지수 백오프로 재시도하고, 4xx는 재시도해도 소용없으므로 즉시 포기한다.
     */
    public Document get(String url) {
        Exception lastError = null;

        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            try {
                return Jsoup.connect(url)
                        .userAgent(properties.getUserAgent())
                        .header("Accept", "text/html,*/*")
                        .timeout(properties.getTimeoutMs())
                        .get();
            } catch (HttpStatusException e) {
                if (e.getStatusCode() >= 400 && e.getStatusCode() < 500) {
                    throw new CrawlerFetchException("HTTP " + e.getStatusCode() + " (재시도 안 함) — " + url, e);
                }
                lastError = e;
            } catch (IOException e) {
                lastError = e;
            }

            if (attempt < properties.getMaxRetries()) {
                sleep(properties.getRequestDelayMs() * (1L << attempt));
            }
        }

        throw new CrawlerFetchException("수집 실패 — " + url, lastError);
    }

    /** 목록/상세 요청 사이에 넣는 고정 딜레이. */
    public void politeDelay() {
        sleep(properties.getRequestDelayMs());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
