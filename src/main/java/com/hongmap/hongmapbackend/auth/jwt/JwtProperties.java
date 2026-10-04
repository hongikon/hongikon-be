package com.hongmap.hongmapbackend.auth.jwt;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private final String secret;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    /**
     * 방금 로테이션된 "직전" refresh 토큰을 이 초 안에 다시 내면 동시 재발급(웹 여러 탭)·응답 유실 재시도로 보고
     * 새 세션을 하나 더 내준다. 0 이면 유예 없음(직전 토큰은 즉시 401).
     */
    private final long refreshReuseGraceSeconds;
    /** 유저당 동시에 살아 있는 refresh 세션(기기·브라우저) 수 상한. 넘으면 가장 오래 안 쓴 세션부터 지운다. */
    private final int maxSessionsPerUser;

    @ConstructorBinding
    public JwtProperties(String secret, long accessTokenExpiration, long refreshTokenExpiration,
                         @DefaultValue("60") long refreshReuseGraceSeconds,
                         @DefaultValue("10") int maxSessionsPerUser) {
        this.secret = secret;
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.refreshReuseGraceSeconds = Math.max(0, refreshReuseGraceSeconds);
        this.maxSessionsPerUser = Math.max(1, maxSessionsPerUser);
    }

    /** 단위 테스트용 — 세션 관련 값은 기본값(유예 60초, 세션 10개). */
    public JwtProperties(String secret, long accessTokenExpiration, long refreshTokenExpiration) {
        this(secret, accessTokenExpiration, refreshTokenExpiration, 60, 10);
    }
}
