package com.hongmap.hongmapbackend.push;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.properties의 push.* 값 바인딩.
 */
@Getter
@ConfigurationProperties(prefix = "push")
public class PushProperties {

    /** false면 새 소식 푸시를 아예 보내지 않는다(대상 조회도 안 함). */
    private final boolean enabled;

    /** Expo Push API 발송 엔드포인트. */
    private final String expoUrl;

    /**
     * Expo 계정의 "Enhanced Security for Push Notifications"를 켰을 때만 필요한 액세스 토큰. 비어 있으면 헤더를 붙이지 않는다.
     */
    private final String expoAccessToken;

    private final int connectTimeoutMs;

    private final int readTimeoutMs;

    /**
     * 작성일이 이 일수보다 오래된 새 소식은 푸시하지 않는다. 처음 크롤링하거나 게시판을 새로 추가했을 때
     * 과거 글이 한꺼번에 "신규"로 저장되면서 수십 건의 푸시가 쏟아지는 것을 막는다.
     */
    private final int newsMaxAgeDays;

    /**
     * 캠퍼스 새 제보 알림 빈도 제한(분). 한 유저는 이 시간 안에 새 제보 푸시를 한 번만 받는다 — 축제처럼 제보 승인이 몰릴 때 스팸 방지.
     */
    private final int reportNewThrottleMinutes;

    /**
     * 관리자 알림 묶음 간격(초). 종류(새 제보·새 문의·자동 숨김)마다 이 시간에 푸시 한 번까지 — 사이에 들어온 건은 끝날 때 "N건"으로 묶는다.
     */
    private final int adminAlertWindowSeconds;

    public PushProperties(boolean enabled, String expoUrl, String expoAccessToken, int connectTimeoutMs, int readTimeoutMs,
                          int newsMaxAgeDays, @DefaultValue("30") int reportNewThrottleMinutes,
                          @DefaultValue("120") int adminAlertWindowSeconds) {
        this.enabled = enabled;
        this.expoUrl = expoUrl;
        this.expoAccessToken = expoAccessToken;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.newsMaxAgeDays = newsMaxAgeDays;
        this.reportNewThrottleMinutes = reportNewThrottleMinutes;
        this.adminAlertWindowSeconds = adminAlertWindowSeconds;
    }
}
