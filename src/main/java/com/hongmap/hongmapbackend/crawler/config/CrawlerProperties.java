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

    /**
     * 증분 수집 — 한 페이지의 가장 오래된(마지막) 글이 이미 저장돼 있으면 다음 페이지는 요청하지 않는다.
     * 목록은 최신순이라 그 뒤는 전부 이전 실행에서 본 구간이다. false면 예전처럼 default-pages만큼 다 훑는다(비상 스위치).
     */
    private final boolean incremental;

    /**
     * 동시에 크롤링할 "서버" 수. 게시판은 호스트명이 아니라 접속 IP 기준으로 묶는다 — 학과 .do 게시판 대부분이
     * 서로 다른 서브도메인이어도 같은 서버(IP)라, 호스트명 기준으로 병렬화하면 학교 서버 한 대를 동시에 때리게 된다.
     * 같은 서버 안에서는 항상 한 번에 한 요청 + request-delay-ms 간격. 1이면 완전 순차.
     */
    private final int parallelServers;

    /** 게시판이 이 횟수만큼 연속(실행 단위)으로 실패하면 잠시 건너뛴다. 0 이하면 끔. */
    private final int failureThreshold;

    /** 연속 실패로 건너뛸 시간(분). 지나면 한 번 다시 시도해 성공하면 정상화, 실패하면 다시 이만큼 쉰다. */
    private final long failureCooldownMinutes;
}
