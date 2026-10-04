package com.hongmap.hongmapbackend.auth.token;

import com.hongmap.hongmapbackend.auth.dto.TokenResponse;
import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 유저당 여러 로그인 세션 + 재발급 유예(H2). 예전엔 유저당 refresh row 1개라 두 번째 로그인·동시 재발급·응답 유실 재시도에서
 * 다른 쪽이 401 로 로그아웃됐다. 로그인은 /auth/token/exchange 와 같은 경로인 RefreshTokenService.issueTokenPair 로 직접 부른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenSessionIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired RefreshTokenService refreshTokenService;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired RefreshTokenCleanup refreshTokenCleanup;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired JdbcTemplate jdbcTemplate;
    @Value("${jwt.secret}") String jwtSecret;

    @Test
    void 두_기기에서_로그인해도_두_refresh_토큰_모두_재발급된다() throws Exception {
        User me = newUser();
        TokenResponse phone = refreshTokenService.issueTokenPair(me.getId());
        TokenResponse web = refreshTokenService.issueTokenPair(me.getId());

        assertThat(phone.refreshToken()).isNotEqualTo(web.refreshToken()); // jti — 같은 초에 만들어도 다르다
        assertThat(sessions(me)).isEqualTo(2);

        String phone2 = reissueOk(phone.refreshToken());
        String web2 = reissueOk(web.refreshToken());
        reissueOk(phone2);
        reissueOk(web2);
        assertThat(sessions(me)).isEqualTo(2);
    }

    @Test
    void 로그아웃하면_그_세션만_지워지고_다른_기기는_유지된다() throws Exception {
        User me = newUser();
        TokenResponse phone = refreshTokenService.issueTokenPair(me.getId());
        TokenResponse web = refreshTokenService.issueTokenPair(me.getId());

        logout(web.refreshToken());

        assertThat(reissueStatus(web.refreshToken())).isEqualTo(401);
        reissueOk(phone.refreshToken());
        assertThat(sessions(me)).isEqualTo(1);
    }

    @Test
    void 같은_토큰_재발급이_유예_안에_다시_오면_두_쪽_모두_계속_쓸_수_있다() throws Exception {
        User me = newUser();
        String original = refreshTokenService.issueTokenPair(me.getId()).refreshToken();

        String first = reissueOk(original);   // 탭 A(또는 응답을 잃은 앱의 첫 요청)
        String second = reissueOk(original);  // 탭 B(또는 재시도) — 직전 토큰, 유예 안 → 새 세션

        assertThat(second).isNotEqualTo(first);
        assertThat(sessions(me)).isEqualTo(2);
        // 유예가 지난 뒤(다음 재발급은 보통 30분 뒤)에도 두 쪽 토큰 모두 살아 있어야 한다
        expireGraceOf(me);
        reissueOk(first);
        reissueOk(second);
    }

    @Test
    void 같은_토큰으로_동시에_재발급해도_둘_다_성공한다() throws Exception {
        User me = newUser();
        String original = refreshTokenService.issueTokenPair(me.getId()).refreshToken();

        int n = 2;
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<MvcResult> call = () -> {
                    ready.countDown();
                    go.await();
                    return reissue(original);
                };
                results.add(pool.submit(call));
            }
            ready.await();
            go.countDown();

            List<String> tokens = new ArrayList<>();
            for (Future<MvcResult> result : results) {
                MvcResult r = result.get();
                assertThat(r.getResponse().getStatus()).isEqualTo(200);
                tokens.add(JsonPath.read(r.getResponse().getContentAsString(), "$.refreshToken"));
            }
            assertThat(tokens.get(0)).isNotEqualTo(tokens.get(1));
            assertThat(sessions(me)).isEqualTo(2);
            for (String token : tokens) {
                reissueOk(token);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 유예가_지난_직전_토큰은_401이고_현재_토큰은_그대로_쓸_수_있다() throws Exception {
        User me = newUser();
        String original = refreshTokenService.issueTokenPair(me.getId()).refreshToken();
        String rotated = reissueOk(original);

        expireGraceOf(me);

        assertThat(reissueStatus(original)).isEqualTo(401);
        assertThat(sessions(me)).isEqualTo(1); // 세션을 지우지는 않는다
        reissueOk(rotated);
    }

    @Test
    void 세션은_유저당_10개까지_가장_오래_안_쓴_것부터_지운다() throws Exception {
        User me = newUser();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            tokens.add(refreshTokenService.issueTokenPair(me.getId()).refreshToken());
        }

        assertThat(sessions(me)).isEqualTo(10);
        assertThat(reissueStatus(tokens.get(0))).isEqualTo(401);
        reissueOk(tokens.get(1));
        reissueOk(tokens.get(10));
    }

    @Test
    void 탈퇴하면_모든_세션이_지워진다() throws Exception {
        User me = newUser();
        TokenResponse phone = refreshTokenService.issueTokenPair(me.getId());
        refreshTokenService.issueTokenPair(me.getId());
        reissueOk(refreshTokenService.issueTokenPair(me.getId()).refreshToken());
        assertThat(sessions(me)).isEqualTo(3);

        mockMvc.perform(delete("/auth/me").header("Authorization", "Bearer " + phone.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(me.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Long.class, me.getId())).isZero();
    }

    @Test
    void 배포_전에_발급된_jti_없는_토큰과_기존_row도_그대로_재발급된다() throws Exception {
        User me = newUser();
        Instant now = Instant.now();
        String legacy = Jwts.builder()
                .subject(String.valueOf(me.getId()))
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        // 옛 서버가 남긴 row: previous_token_hash·rotated_at 없음
        refreshTokenRepository.save(new RefreshToken(me, sha256(legacy), LocalDateTime.now().plusDays(14)));

        String rotated = reissueOk(legacy);
        assertThat(jwtTokenProvider.getUserId(rotated)).isEqualTo(me.getId());
        assertThat(sessions(me)).isEqualTo(1);
        reissueOk(rotated);
    }

    @Test
    void 만료된_세션은_정기_정리와_로그인_때_지워진다() throws Exception {
        User me = newUser();
        refreshTokenRepository.save(new RefreshToken(me, sha256("old-1-" + UUID.randomUUID()), LocalDateTime.now().minusDays(1)));
        refreshTokenRepository.save(new RefreshToken(me, sha256("old-2-" + UUID.randomUUID()), LocalDateTime.now().minusDays(1)));

        refreshTokenService.issueTokenPair(me.getId());
        assertThat(sessions(me)).isEqualTo(1); // 로그인 때 그 유저의 만료 세션 정리

        refreshTokenRepository.save(new RefreshToken(me, sha256("old-3-" + UUID.randomUUID()), LocalDateTime.now().minusDays(1)));
        refreshTokenCleanup.deleteExpired();
        assertThat(sessions(me)).isEqualTo(1);
    }

    private User newUser() {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("세션테스트").build());
    }

    private MvcResult reissue(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andReturn();
    }

    private int reissueStatus(String refreshToken) throws Exception {
        return reissue(refreshToken).getResponse().getStatus();
    }

    /** 재발급이 200 이어야 하고, 새 refresh 토큰을 돌려준다. */
    private String reissueOk(String refreshToken) throws Exception {
        MvcResult result = reissue(refreshToken);
        assertThat(result.getResponse().getStatus()).as("reissue status").isEqualTo(200);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
    }

    private void logout(String refreshToken) throws Exception {
        mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private long sessions(User user) {
        return refreshTokenRepository.countByUser_Id(user.getId());
    }

    /** 로테이션 시각을 유예(60초)보다 앞으로 돌려 "한참 뒤"를 흉내 낸다. */
    private void expireGraceOf(User user) {
        jdbcTemplate.update("UPDATE refresh_tokens SET rotated_at = ? WHERE user_id = ? AND rotated_at IS NOT NULL",
                LocalDateTime.now().minusMinutes(5), user.getId());
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
