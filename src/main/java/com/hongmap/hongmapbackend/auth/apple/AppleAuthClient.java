package com.hongmap.hongmapbackend.auth.apple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * Apple 서버 호출(토큰 교환·폐기). App Store 심사 가이드라인 5.1.1(v): Sign in with Apple을 제공하는 앱은 계정 삭제 시
 * Apple 토큰도 폐기해야 한다 — 그래서 로그인 때 authorization code를 Apple refresh 토큰으로 바꿔 두었다가 탈퇴 때 revoke한다.
 *
 * <p>두 호출 모두 "best effort"다: 키 설정(app.apple.team-id/key-id/private-key)이 없거나 Apple 호출이 실패해도
 * 예외를 던지지 않고 로그만 남긴다. 로그인·탈퇴 자체는 막지 않는다.
 */
@Slf4j
@Component
public class AppleAuthClient {

    /** client_secret JWT 유효 시간. Apple 상한은 6개월이지만 요청마다 새로 만들므로 짧게 둔다. */
    private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(5);

    private final AppleProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    /** private key 파싱 결과. 설정이 없거나 깨졌으면 null. */
    private final PrivateKey signingKey;

    @Autowired
    public AppleAuthClient(AppleProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties)), Clock.systemUTC());
    }

    /** 테스트에서 MockRestServiceServer를 붙인 builder를 넘기기 위한 생성자. */
    AppleAuthClient(AppleProperties properties, RestClient.Builder builder, Clock clock) {
        this.properties = properties;
        this.restClient = builder.build();
        this.clock = clock;
        this.signingKey = properties.isRevocationConfigured() ? parsePrivateKey(properties.getPrivateKey()) : null;
        if (signingKey == null) {
            log.warn("Apple 키 설정(APPLE_TEAM_ID/APPLE_KEY_ID/APPLE_PRIVATE_KEY)이 없거나 올바르지 않아 "
                    + "Apple refresh 토큰 저장과 탈퇴 시 토큰 폐기를 건너뜁니다. Apple 로그인 자체는 동작합니다.");
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(AppleProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return requestFactory;
    }

    public boolean isConfigured() {
        return signingKey != null;
    }

    /**
     * 앱이 받은 authorization code(5분 유효, 1회용)를 Apple refresh 토큰으로 바꾼다.
     * 키 설정이 없거나 실패하면 빈 값.
     */
    public Optional<String> exchangeForRefreshToken(String authorizationCode, String clientId) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            return Optional.empty();
        }
        if (!isConfigured()) {
            log.warn("Apple 키 설정이 없어 authorization code 교환을 건너뜁니다(탈퇴 시 Apple 토큰 폐기 불가).");
            return Optional.empty();
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", createClientSecret(clientId));
            form.add("code", authorizationCode);
            form.add("grant_type", "authorization_code");
            TokenResponse response = restClient.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.refreshToken() == null || response.refreshToken().isBlank()) {
                log.warn("Apple 토큰 교환 응답에 refresh_token이 없습니다.");
                return Optional.empty();
            }
            return Optional.of(response.refreshToken());
        } catch (RuntimeException e) {
            // 응답 본문(에러 코드)만 남기고 code·client_secret은 로그에 남기지 않는다.
            log.warn("Apple authorization code 교환 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Apple refresh 토큰을 폐기한다(사용자의 "Apple로 로그인한 앱" 목록에서 이 앱 연결이 끊긴다).
     * 성공하면 true. 설정이 없거나, 토큰이 없거나, 실패하면 로그만 남기고 false.
     */
    public boolean revokeQuietly(String refreshToken, String clientId) {
        if (refreshToken == null || refreshToken.isBlank() || clientId == null || clientId.isBlank()) {
            log.warn("저장된 Apple refresh 토큰이 없어 Apple 토큰 폐기를 건너뜁니다.");
            return false;
        }
        if (!isConfigured()) {
            log.warn("Apple 키 설정이 없어 탈퇴 사용자의 Apple 토큰 폐기를 건너뜁니다.");
            return false;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", createClientSecret(clientId));
            form.add("token", refreshToken);
            form.add("token_type_hint", "refresh_token");
            restClient.post()
                    .uri(properties.getRevokeUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Apple 토큰 폐기 완료");
            return true;
        } catch (RuntimeException e) {
            log.warn("Apple 토큰 폐기 실패(탈퇴는 그대로 진행): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Apple 토큰 API용 client_secret(ES256 JWT). iss=팀 ID, sub=client_id(번들 ID), aud=https://appleid.apple.com.
     */
    String createClientSecret(String clientId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .header().keyId(properties.getKeyId()).and()
                .issuer(properties.getTeamId())
                .subject(clientId)
                .audience().add(AppleIdentityTokenVerifier.ISSUER).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(CLIENT_SECRET_TTL)))
                .signWith(signingKey, Jwts.SIG.ES256)
                .compact();
    }

    /** .p8(PEM, PKCS#8) 내용을 EC 개인키로. 환경 변수에 "\n" 문자열로 넣은 줄바꿈도 받아준다. 실패하면 null. */
    static PrivateKey parsePrivateKey(String pem) {
        try {
            String base64 = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            log.error("APPLE_PRIVATE_KEY를 해석하지 못했습니다(.p8 파일 내용 전체를 넣었는지 확인): {}", e.getClass().getSimpleName());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("refresh_token") String refreshToken) {
    }
}
