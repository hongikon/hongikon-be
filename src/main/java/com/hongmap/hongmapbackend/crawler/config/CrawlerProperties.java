package com.hongmap.hongmapbackend.crawler.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.properties의 crawler.* 값 바인딩. hongmap(프론트) scripts/crawler/config.mjs의
 * PAGE_SIZE/DEFAULT_PAGES/REQUEST_DELAY_MS/MAX_RETRIES/REQUEST_TIMEOUT_MS/USER_AGENT에 대응.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    /** 한 번의 목록 요청으로 가져올 게시글 수. */
    private final int pageSize;

    /** 한 게시판당 훑을 목록 페이지 수. */
    private final int defaultPages;

    /** 연속 요청 사이 대기(ms). 학교 서버 부담을 줄이기 위한 최소한의 예의. */
    private final long requestDelayMs;

    /** 요청 실패 시 재시도 횟수(4xx는 재시도하지 않음). */
    private final int maxRetries;

    /** 요청 타임아웃(ms). */
    private final int timeoutMs;

    private final String userAgent;
}
