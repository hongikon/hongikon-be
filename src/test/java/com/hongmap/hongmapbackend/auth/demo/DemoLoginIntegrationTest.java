package com.hongmap.hongmapbackend.auth.demo;

import com.hongmap.hongmapbackend.auth.apple.AppleRevocationService;
import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.auth.oauth.KakaoUnlinkClient;
import com.hongmap.hongmapbackend.auth.token.RefreshTokenRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.hongmap.hongmapbackend.user.UserRole;
import com.hongmap.hongmapbackend.user.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /auth/demo 를 켠 상태의 전체 흐름(H2). 테스트마다 다른 접속 IP 를 써서 시도 한도가 서로 섞이지 않게 한다.
 * 테스트 전용 가짜 자격 증명이다(운영 값 아님).
 */
@SpringBootTest(properties = {
        "app.demo-login.enabled=true",
        "app.demo-login.username=review-demo",
        "app.demo-login.password=test-only-password-1234",
        "app.demo-login.rate-limit.max-attempts=5"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoLoginIntegrationTest {

    private static final String USERNAME = "review-demo";
    private static final String PASSWORD = "test-only-password-1234";
    private static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserService userService;

    @MockitoBean KakaoUnlinkClient kakaoUnlinkClient;
    @MockitoBean AppleRevocationService appleRevocationService;

    private String ip;

    @BeforeEach
    void setUp() {
        ip = "203.0.113." + IP_SEQ.getAndIncrement();
        // 이전 테스트가 남긴 데모 회원은 지운다(탈퇴 처리로 세션까지).
        userRepository.findBySocialTypeAndSocialId(SocialType.DEMO, DemoLoginService.DEMO_SOCIAL_ID)
                .ifPresent(user -> userService.withdraw(user.getId()));
    }

    private ResultActions login(String body) throws Exception {
        return mockMvc.perform(post("/auth/demo").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }));
    }

    private static String body(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    @Test
    void 맞는_아이디_비밀번호면_토큰_쌍을_주고_다시_로그인해도_같은_회원이다() throws Exception {
        MvcResult first = login(body(USERNAME, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();
        String json = first.getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, Object>>read(json, "$")).containsOnlyKeys("accessToken", "refreshToken");

        User user = userRepository.findBySocialTypeAndSocialId(SocialType.DEMO, DemoLoginService.DEMO_SOCIAL_ID).orElseThrow();
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getAppNickname()).isNull();
        String accessToken = JsonPath.read(json, "$.accessToken");
        assertThat(jwtTokenProvider.getUserId(accessToken)).isEqualTo(user.getId());
        assertThat(jwtTokenProvider.isAccessToken(accessToken)).isTrue();
        assertThat(refreshTokenRepository.countByUser_Id(user.getId())).isEqualTo(1L);

        String second = login(body(USERNAME, PASSWORD)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(jwtTokenProvider.getUserId(JsonPath.read(second, "$.accessToken"))).isEqualTo(user.getId());
        assertThat(userRepository.findAll().stream().filter(u -> u.getSocialType() == SocialType.DEMO)).hasSize(1);
        // 로그인마다 세션이 하나씩(카카오·Apple 과 같은 issueTokenPair)
        assertThat(refreshTokenRepository.countByUser_Id(user.getId())).isEqualTo(2L);

        // 발급받은 refresh 토큰으로 재발급도 된다
        String refreshToken = JsonPath.read(second, "$.refreshToken");
        mockMvc.perform(post("/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk());
    }

    @Test
    void 틀리면_어느_쪽이_틀렸는지_알리지_않는_401이고_회원을_만들지_않는다() throws Exception {
        String wrongPassword = login(body(USERNAME, "wrong-password-0000")).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrongUsername = login(body("someone-else", PASSWORD)).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(wrongPassword).isEqualTo(wrongUsername).contains(DemoLoginService.INVALID_CREDENTIALS_MESSAGE);

        login("{}").andExpect(status().isUnauthorized());
        login("not json").andExpect(status().isUnauthorized());
        login(body(USERNAME.toUpperCase(), PASSWORD)).andExpect(status().isUnauthorized());

        assertThat(userRepository.findBySocialTypeAndSocialId(SocialType.DEMO, DemoLoginService.DEMO_SOCIAL_ID)).isEmpty();
    }

    @Test
    void 같은_IP_에서_한도를_넘으면_429이고_맞는_비밀번호도_막힌다() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(body(USERNAME, "wrong-password-" + i)).andExpect(status().isUnauthorized());
        }
        login(body(USERNAME, PASSWORD)).andExpect(status().isTooManyRequests());

        // 다른 IP 는 영향 없음
        ip = "198.51.100.7";
        login(body(USERNAME, PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void 데모_회원이_ADMIN_이면_거절한다() throws Exception {
        login(body(USERNAME, PASSWORD)).andExpect(status().isOk());
        User user = userRepository.findBySocialTypeAndSocialId(SocialType.DEMO, DemoLoginService.DEMO_SOCIAL_ID).orElseThrow();
        user.changeRole(UserRole.ADMIN);
        userRepository.save(user);

        login(body(USERNAME, PASSWORD)).andExpect(status().isUnauthorized());
        assertThat(refreshTokenRepository.countByUser_Id(user.getId())).isEqualTo(1L);
    }

    @Test
    void 데모_회원_탈퇴는_카카오_Apple_을_부르지_않는다() throws Exception {
        String json = login(body(USERNAME, PASSWORD)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(json, "$.accessToken");
        Long userId = jwtTokenProvider.getUserId(accessToken);

        mockMvc.perform(delete("/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(refreshTokenRepository.countByUser_Id(userId)).isZero();
        verifyNoInteractions(kakaoUnlinkClient, appleRevocationService);

        // 탈퇴 뒤 다시 로그인하면 새 데모 회원이 만들어진다
        login(body(USERNAME, PASSWORD)).andExpect(status().isOk());
        assertThat(userRepository.findBySocialTypeAndSocialId(SocialType.DEMO, DemoLoginService.DEMO_SOCIAL_ID)).isPresent();
    }

    @Test
    void IP_는_끝부분을_가려_로그에_남긴다() {
        assertThat(DemoLoginService.maskIp("203.0.113.42")).isEqualTo("203.0.113.*");
        assertThat(DemoLoginService.maskIp("2001:db8:85a3:0:0:8a2e:370:7334")).isEqualTo("2001:db8:85a3:*");
        assertThat(DemoLoginService.maskIp(null)).isEqualTo("unknown");
    }
}
