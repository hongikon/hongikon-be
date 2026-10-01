package com.hongmap.hongmapbackend.security;

import com.hongmap.hongmapbackend.auth.jwt.JwtProperties;
import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderSecretTest {

    @Test
    void 비어있거나_짧은_비밀키면_기동하지_않는다() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("", 1000, 1000)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties(null, 1000, 1000)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("short-secret", 1000, 1000)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 충분한_비밀키면_토큰을_발급하고_검증한다() {
        JwtTokenProvider provider = new JwtTokenProvider(
                new JwtProperties("0123456789abcdef0123456789abcdef", 60_000, 60_000));
        String token = provider.generateAccessToken(42L);
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.isAccessToken(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(42L);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        JwtTokenProvider a = new JwtTokenProvider(new JwtProperties("0123456789abcdef0123456789abcdef", 60_000, 60_000));
        JwtTokenProvider b = new JwtTokenProvider(new JwtProperties("fedcba9876543210fedcba9876543210", 60_000, 60_000));
        assertThat(b.validateToken(a.generateAccessToken(1L))).isFalse();
    }
}
