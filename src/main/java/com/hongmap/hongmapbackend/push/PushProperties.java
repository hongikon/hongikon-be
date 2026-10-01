package com.hongmap.hongmapbackend.push;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.properties의 push.* 값 바인딩.
 */
@Getter
@RequiredArgsConstructor
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
}
