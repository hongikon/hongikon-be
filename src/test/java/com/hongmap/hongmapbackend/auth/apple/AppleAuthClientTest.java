package com.hongmap.hongmapbackend.auth.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Apple 토큰 교환·폐기 요청 형태와 client_secret(ES256 JWT). 실제 Apple은 호출하지 않는다(MockRestServiceServer).
 */
class AppleAuthClientTest {

    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";
    private static final KeyPair EC_KEY = ecKeyPair();
    private static final Instant NOW = Instant.parse("2026-10-02T03:00:00Z");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private static KeyPair ecKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** .p8 파일과 같은 PEM 문자열. */
    private static String pem(KeyPair keyPair, String lineBreak) {
        String base64 = Base64.getMimeEncoder(64, lineBreak.getBytes(StandardCharsets.UTF_8))
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----" + lineBreak + base64 + lineBreak + "-----END PRIVATE KEY-----";
    }

    private static AppleProperties properties(String teamId, String keyId, String privateKey) {
        return new AppleProperties(List.of("com.hongikon.app"), teamId, keyId, privateKey,
                "https://appleid.apple.com/auth/keys", TOKEN_URL, REVOKE_URL, 1000, 1000);
    }

    private AppleAuthClient configuredClient() {
        return new AppleAuthClient(properties("TEAM123456", "KEY1234567", pem(EC_KEY, "\n")), builder,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Map<String, String> form(MockClientHttpRequest request) {
        return Arrays.stream(request.getBodyAsString().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(kv -> kv[0], kv -> URLDecoder.decode(kv[1], StandardCharsets.UTF_8)));
    }

    @Test
    void 코드를_refresh_토큰으로_교환한다() {
        AtomicReference<Map<String, String>> sent = new AtomicReference<>();
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> sent.set(form((MockClientHttpRequest) request)))
                .andRespond(withSuccess("""
                        {"access_token":"a.b.c","token_type":"Bearer","expires_in":3600,
                         "refresh_token":"r.apple-refresh","id_token":"x.y.z"}
                        """, MediaType.APPLICATION_JSON));

        Optional<String> refreshToken = configuredClient().exchangeForRefreshToken("auth-code", "com.hongikon.app");

        server.verify();
        assertThat(refreshToken).contains("r.apple-refresh");
        assertThat(sent.get())
                .containsEntry("client_id", "com.hongikon.app")
                .containsEntry("code", "auth-code")
                .containsEntry("grant_type", "authorization_code");

        // client_secret: ES256, kid=KEY ID, iss=팀 ID, sub=client_id, aud=https://appleid.apple.com
        Jws<Claims> secret = Jwts.parser().verifyWith(EC_KEY.getPublic())
                .clock(() -> java.util.Date.from(NOW)).build()
                .parseSignedClaims(sent.get().get("client_secret"));
        assertThat(secret.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(secret.getHeader().getKeyId()).isEqualTo("KEY1234567");
        assertThat(secret.getPayload().getIssuer()).isEqualTo("TEAM123456");
        assertThat(secret.getPayload().getSubject()).isEqualTo("com.hongikon.app");
        assertThat(secret.getPayload().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(secret.getPayload().getExpiration()).isAfter(java.util.Date.from(NOW));
    }

    @Test
    void 교환이_실패하면_예외_없이_빈_값() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withBadRequest().body("{\"error\":\"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

        assertThat(configuredClient().exchangeForRefreshToken("used-code", "com.hongikon.app")).isEmpty();
        server.verify();
    }

    @Test
    void refresh_토큰을_폐기한다() {
        AtomicReference<Map<String, String>> sent = new AtomicReference<>();
        server.expect(requestTo(REVOKE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> sent.set(form((MockClientHttpRequest) request)))
                .andRespond(withSuccess());

        boolean revoked = configuredClient().revokeQuietly("r.apple-refresh", "com.hongikon.app.preview");

        server.verify();
        assertThat(revoked).isTrue();
        assertThat(sent.get())
                .containsEntry("client_id", "com.hongikon.app.preview")
                .containsEntry("token", "r.apple-refresh")
                .containsEntry("token_type_hint", "refresh_token")
                .containsKey("client_secret");
    }

    @Test
    void 폐기가_실패해도_예외를_던지지_않는다() {
        server.expect(requestTo(REVOKE_URL)).andRespond(withBadRequest());

        // FAILED 는 재시도 대기열 대상(SKIPPED 와 구분)
        assertThat(configuredClient().revoke("r.apple-refresh", "com.hongikon.app"))
                .isEqualTo(AppleAuthClient.RevokeResult.FAILED);
        server.verify();
    }

    @Test
    void 키_설정이_없으면_Apple을_부르지_않고_건너뛴다() {
        AppleAuthClient client = new AppleAuthClient(properties("", "", ""), builder, Clock.systemUTC());

        assertThat(client.isConfigured()).isFalse();
        assertThat(client.exchangeForRefreshToken("auth-code", "com.hongikon.app")).isEmpty();
        assertThat(client.revokeQuietly("r.apple-refresh", "com.hongikon.app")).isFalse();
        assertThat(client.revoke("r.apple-refresh", "com.hongikon.app")).isEqualTo(AppleAuthClient.RevokeResult.SKIPPED);
        server.verify(); // 요청 0건
    }

    @Test
    void 키가_깨져_있어도_기동은_되고_건너뛴다() {
        AppleAuthClient client = new AppleAuthClient(properties("TEAM", "KEY", "not-a-pem"), builder, Clock.systemUTC());

        assertThat(client.isConfigured()).isFalse();
        assertThat(client.exchangeForRefreshToken("auth-code", "com.hongikon.app")).isEmpty();
    }

    @Test
    void 환경_변수용_한_줄_PEM도_읽는다() {
        String oneLine = pem(EC_KEY, "\n").replace("\n", "\\n"); // 실제 줄바꿈 대신 \n 두 글자

        assertThat(AppleAuthClient.parsePrivateKey(oneLine)).isEqualTo(EC_KEY.getPrivate());
    }

    @Test
    void 저장된_토큰이_없으면_폐기를_건너뛴다() {
        assertThat(configuredClient().revokeQuietly(null, "com.hongikon.app")).isFalse();
        server.verify();
    }
}
