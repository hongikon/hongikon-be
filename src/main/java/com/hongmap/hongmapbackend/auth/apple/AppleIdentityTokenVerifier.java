package com.hongmap.hongmapbackend.auth.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 앱이 보낸 Apple identity token(JWT)을 검증한다.
 * <ul>
 *     <li>서명: RS256, Apple JWKS의 kid 키로 검증. 모르는 kid면 JWKS를 다시 받아 본다(키 교체 대응).</li>
 *     <li>iss = https://appleid.apple.com, aud ∈ app.apple.client-ids, exp 유효(시계 오차 60초 허용).</li>
 *     <li>nonce(필수): 앱은 원본 nonce 의 SHA-256(16진수)을 Apple 에 넘기고 원본을 서버로 보낸다. Apple 은 받은 값을
 *     그대로 토큰에 넣으므로 토큰 nonce == sha256hex(원본) 이어야 통과한다. 토큰의 nonce 는 누구나 읽을 수 있으므로
 *     "원본 그대로 일치"는 받지 않는다 — 그러면 탈취한 토큰에서 nonce 를 꺼내 그대로 보내 재사용(replay)할 수 있다.</li>
 * </ul>
 * 실패는 모두 401로 응답한다(어느 검사에서 걸렸는지는 로그에만 남긴다).
 */
@Slf4j
@Component
public class AppleIdentityTokenVerifier {

    public static final String ISSUER = "https://appleid.apple.com";

    /** 모르는 kid가 계속 들어와도 JWKS를 이 간격보다 자주 다시 받지 않는다(외부 요청 남발 방지). */
    static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(1);

    private static final long CLOCK_SKEW_SECONDS = 60;

    private final AppleJwksSource jwksSource;
    private final Set<String> allowedClientIds;
    private final Clock clock;

    private volatile Map<String, PublicKey> keysByKid = Map.of();
    private volatile Instant lastRefreshAt;

    @Autowired
    public AppleIdentityTokenVerifier(AppleJwksSource jwksSource, AppleProperties properties) {
        this(jwksSource, properties.getClientIds(), Clock.systemUTC());
    }

    AppleIdentityTokenVerifier(AppleJwksSource jwksSource, List<String> allowedClientIds, Clock clock) {
        this.jwksSource = jwksSource;
        this.allowedClientIds = allowedClientIds == null ? Set.of() : allowedClientIds.stream()
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.clock = clock;
    }

    public AppleIdentity verify(String identityToken, String nonce) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(ProtectedHeader header) {
                            if (!"RS256".equals(header.getAlgorithm())) {
                                throw new InvalidAppleTokenException("지원하지 않는 서명 알고리즘: " + header.getAlgorithm());
                            }
                            return publicKeyFor(header.getKeyId());
                        }
                    })
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();
        } catch (InvalidAppleTokenException e) {
            throw unauthorized(e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthorized(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        if (claims.getExpiration() == null) {
            throw unauthorized("exp 없음");
        }
        String clientId = claims.getAudience() == null ? null : claims.getAudience().stream()
                .filter(allowedClientIds::contains)
                .findFirst()
                .orElse(null);
        if (clientId == null) {
            throw unauthorized("허용되지 않은 aud: " + claims.getAudience());
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw unauthorized("sub 없음");
        }
        if (nonce == null || nonce.isBlank()) {
            throw unauthorized("nonce 없음");
        }
        if (!nonceMatches(nonce, claims.get("nonce", String.class))) {
            throw unauthorized("nonce 불일치");
        }
        return new AppleIdentity(subject, clientId);
    }

    private PublicKey publicKeyFor(String kid) {
        if (kid == null) {
            throw new InvalidAppleTokenException("kid 없음");
        }
        PublicKey key = keysByKid.get(kid);
        if (key == null && refreshKeysIfAllowed()) {
            key = keysByKid.get(kid);
        }
        if (key == null) {
            throw new InvalidAppleTokenException("알 수 없는 kid: " + kid);
        }
        return key;
    }

    /** JWKS를 다시 받는다. 최근에 이미 받았으면 건너뛰고 false. */
    private synchronized boolean refreshKeysIfAllowed() {
        Instant now = clock.instant();
        if (lastRefreshAt != null && now.isBefore(lastRefreshAt.plus(MIN_REFRESH_INTERVAL))) {
            return false;
        }
        lastRefreshAt = now;
        try {
            Map<String, PublicKey> fresh = new HashMap<>();
            for (AppleJwk jwk : jwksSource.fetchKeys()) {
                if (jwk.kid() == null || (jwk.alg() != null && !"RS256".equals(jwk.alg()))) {
                    continue;
                }
                try {
                    fresh.put(jwk.kid(), jwk.toPublicKey());
                } catch (IllegalArgumentException e) {
                    log.warn("Apple 공개키 하나를 건너뜀: {}", e.getMessage());
                }
            }
            if (!fresh.isEmpty()) {
                keysByKid = Map.copyOf(fresh);
            }
            return true;
        } catch (RuntimeException e) {
            // Apple 장애 시 기존 캐시는 그대로 두고, 다음 시도는 MIN_REFRESH_INTERVAL 뒤에 한다.
            log.error("Apple 공개키(JWKS)를 가져오지 못했습니다", e);
            return false;
        }
    }

    private static boolean nonceMatches(String nonce, String tokenNonce) {
        if (tokenNonce == null) {
            return false;
        }
        return MessageDigest.isEqual(tokenNonce.getBytes(StandardCharsets.UTF_8),
                sha256Hex(nonce).getBytes(StandardCharsets.UTF_8));
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private static ResponseStatusException unauthorized(String reason) {
        log.info("Apple identity token 검증 실패: {}", reason);
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 Apple 로그인 정보입니다.");
    }

    /** 키 찾기 단계에서 던져 jjwt 밖으로 그대로 꺼내기 위한 내부 예외. */
    private static class InvalidAppleTokenException extends RuntimeException {
        InvalidAppleTokenException(String message) {
            super(message);
        }
    }
}
