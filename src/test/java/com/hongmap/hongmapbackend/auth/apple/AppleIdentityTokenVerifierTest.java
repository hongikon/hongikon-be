package com.hongmap.hongmapbackend.auth.apple;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * identity token 검증: 서명(JWKS 키), iss/aud/exp/nonce, 키 교체 시 JWKS 재조회. 실제 Apple은 호출하지 않는다.
 */
class AppleIdentityTokenVerifierTest {

    private static final AppleTestKeys APPLE = new AppleTestKeys("kid-1");
    private static final List<String> CLIENT_IDS =
            List.of("com.hongmap.alimi", "com.hongmap.alimi.preview", "com.hongmap.alimi.dev");

    /** 돌려줄 키 목록을 바꿀 수 있고, 몇 번 불렸는지 센다. */
    static class StubJwksSource implements AppleJwksSource {
        final List<AppleJwk> keys = new ArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        StubJwksSource(AppleJwk... initial) {
            keys.addAll(List.of(initial));
        }

        @Override
        public List<AppleJwk> fetchKeys() {
            calls.incrementAndGet();
            return List.copyOf(keys);
        }
    }

    /** 테스트 안에서 시간을 앞으로 돌릴 수 있는 시계. */
    static class MutableClock extends Clock {
        Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private final Instant now = Instant.parse("2026-10-02T03:00:00Z");
    private final MutableClock clock = new MutableClock(now);
    private final StubJwksSource source = new StubJwksSource(APPLE.jwk());
    private final AppleIdentityTokenVerifier verifier = new AppleIdentityTokenVerifier(source, CLIENT_IDS, clock);

    private static void assertUnauthorized(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 올바른_토큰이면_sub와_aud를_돌려준다() {
        String token = APPLE.token().now(now).subject("apple-sub-1").audience("com.hongmap.alimi.preview").build();

        AppleIdentity identity = verifier.verify(token, null);

        assertThat(identity.subject()).isEqualTo("apple-sub-1");
        assertThat(identity.clientId()).isEqualTo("com.hongmap.alimi.preview");
    }

    @Test
    void 다른_키로_서명한_토큰은_401() {
        AppleTestKeys attacker = new AppleTestKeys("kid-1");
        String token = APPLE.token().now(now).signedBy(attacker.keyPair).build();

        assertUnauthorized(() -> verifier.verify(token, null));
    }

    @Test
    void 서명을_변조한_토큰은_401() {
        String token = APPLE.token().now(now).build();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertUnauthorized(() -> verifier.verify(tampered, null));
    }

    @Test
    void HS256_등_RS256이_아닌_토큰은_401() {
        String token = Jwts.builder()
                .header().keyId("kid-1").and()
                .issuer(AppleIdentityTokenVerifier.ISSUER).audience().add("com.hongmap.alimi").and()
                .subject("x").expiration(java.util.Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(new byte[32]), Jwts.SIG.HS256)
                .compact();

        assertUnauthorized(() -> verifier.verify(token, null));
    }

    @Test
    void iss가_Apple이_아니면_401() {
        String token = APPLE.token().now(now).issuer("https://evil.example.com").build();

        assertUnauthorized(() -> verifier.verify(token, null));
    }

    @Test
    void aud가_허용_번들ID가_아니면_401() {
        String token = APPLE.token().now(now).audience("com.other.app").build();

        assertUnauthorized(() -> verifier.verify(token, null));
    }

    @Test
    void 만료된_토큰은_401() {
        String token = APPLE.token().now(now.minusSeconds(3600)).expiresAt(now.minusSeconds(120)).build();

        assertUnauthorized(() -> verifier.verify(token, null));
    }

    @Test
    void nonce는_원본의_SHA256이나_원본_그대로_둘_다_통과한다() {
        String rawNonce = "raw-nonce-value-0123456789";

        String hashedInToken = APPLE.token().now(now).nonce(AppleIdentityTokenVerifier.sha256Hex(rawNonce)).build();
        String rawInToken = APPLE.token().now(now).nonce(rawNonce).build();

        assertThat(verifier.verify(hashedInToken, rawNonce).subject()).isNotBlank();
        assertThat(verifier.verify(rawInToken, rawNonce).subject()).isNotBlank();
    }

    @Test
    void nonce가_다르거나_토큰에_없으면_401() {
        String token = APPLE.token().now(now).nonce(AppleIdentityTokenVerifier.sha256Hex("expected")).build();
        String withoutNonce = APPLE.token().now(now).build();

        assertUnauthorized(() -> verifier.verify(token, "something-else"));
        assertUnauthorized(() -> verifier.verify(withoutNonce, "expected"));
    }

    @Test
    void 모르는_kid가_오면_JWKS를_다시_받아_새_키로_검증한다() {
        verifier.verify(APPLE.token().now(now).build(), null);
        assertThat(source.calls.get()).isEqualTo(1);

        // 1분 뒤 Apple이 새 키를 추가(키 교체)
        AppleTestKeys rotated = new AppleTestKeys("kid-2");
        source.keys.add(rotated.jwk());
        clock.instant = now.plusSeconds(61);
        String second = rotated.token().now(clock.instant).build();

        assertThat(verifier.verify(second, null).subject()).isNotBlank();
        assertThat(source.calls.get()).isEqualTo(2);
        // 이미 아는 kid는 다시 받지 않는다
        verifier.verify(APPLE.token().now(clock.instant).build(), null);
        assertThat(source.calls.get()).isEqualTo(2);
    }

    @Test
    void 모르는_kid가_연달아_와도_JWKS는_1분에_한_번만_받는다() {
        verifier.verify(APPLE.token().now(now).build(), null);
        String unknown = APPLE.token().now(now).headerKid("nope").build();

        assertUnauthorized(() -> verifier.verify(unknown, null));
        assertUnauthorized(() -> verifier.verify(unknown, null));

        assertThat(source.calls.get()).isEqualTo(1);
    }

    @Test
    void JWKS_조회가_실패하면_401() {
        AppleIdentityTokenVerifier failing = new AppleIdentityTokenVerifier(() -> {
            throw new IllegalStateException("apple down");
        }, CLIENT_IDS, Clock.fixed(now, ZoneOffset.UTC));

        assertUnauthorized(() -> failing.verify(APPLE.token().now(now).build(), null));
    }
}
