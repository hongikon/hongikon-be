package com.hongmap.hongmapbackend.auth.jwt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@RequiredArgsConstructor
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private final String secret;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
}
