package com.hongmap.hongmapbackend.auth.apple;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.auth.token.RefreshTokenRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /auth/apple 전체 흐름(H2). Apple JWKS는 로컬 키를 돌려주는 목, Apple 토큰 API는 키 설정이 없는 실제
 * AppleAuthClient(=설정 없이도 로그인되는지)를 spy로 감싸 필요할 때만 응답을 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppleLoginIntegrationTest {

    private static final AppleTestKeys APPLE = new AppleTestKeys("it-kid");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserService userService;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @MockitoBean AppleJwksSource jwksSource;
    @MockitoSpyBean AppleAuthClient appleAuthClient;

    @BeforeEach
    void setUp() {
        when(jwksSource.fetchKeys()).thenReturn(List.of(APPLE.jwk()));
    }

    private MvcResult login(String body) throws Exception {
        return mockMvc.perform(post("/auth/apple").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private static String newSub() {
        return "000" + UUID.randomUUID().toString().replace("-", "") + ".0001";
    }

    @Test
    void 처음_로그인하면_회원을_만들고_카카오와_같은_토큰_쌍을_준다() throws Exception {
        String sub = newSub();
        String token = APPLE.token().subject(sub).nonce(AppleIdentityTokenVerifier.sha256Hex("raw-nonce")).build();

        MvcResult result = mockMvc.perform(post("/auth/apple").contentType(MediaType.APPLICATION_JSON).content("""
                        {"identityToken":"%s","authorizationCode":"c123","nonce":"raw-nonce",
                         "fullName":{"givenName":"길동","familyName":"홍"}}
                        """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();

        User user = userRepository.findBySocialTypeAndSocialId(SocialType.APPLE, sub).orElseThrow();
        assertThat(user.getNickname()).isEqualTo("홍길동");
        assertThat(user.getEmail()).isNull();
        // 키 설정이 없는 테스트 환경: 코드 교환을 건너뛰어 Apple refresh 토큰은 저장되지 않는다
        assertThat(user.getAppleRefreshToken()).isNull();

        String json = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(json, "$.accessToken");
        assertThat(jwtTokenProvider.getUserId(accessToken)).isEqualTo(user.getId());
        assertThat(jwtTokenProvider.isAccessToken(accessToken)).isTrue();
        assertThat(refreshTokenRepository.findByUser_Id(user.getId())).isPresent();
        assertThat(JsonPath.<java.util.Map<String, Object>>read(json, "$")).containsOnlyKeys("accessToken", "refreshToken");
    }

    @Test
    void 이름이_없으면_기본_닉네임으로_만들고_다시_로그인하면_같은_회원이다() throws Exception {
        String sub = newSub();

        login("{\"identityToken\":\"%s\"}".formatted(APPLE.token().subject(sub).build()));
        User created = userRepository.findBySocialTypeAndSocialId(SocialType.APPLE, sub).orElseThrow();
        assertThat(created.getNickname()).matches("Apple 사용자 [0-9A-F]{4}");

        MvcResult second = login("""
                {"identityToken":"%s","fullName":{"givenName":"Other","familyName":"Name"}}
                """.formatted(APPLE.token().subject(sub).build()));

        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        String accessToken = JsonPath.read(second.getResponse().getContentAsString(), "$.accessToken");
        assertThat(jwtTokenProvider.getUserId(accessToken)).isEqualTo(created.getId());
        assertThat(userRepository.findById(created.getId()).orElseThrow().getNickname())
                .isEqualTo(created.getNickname()); // 기존 회원 닉네임은 덮어쓰지 않는다
    }

    @Test
    void 검증에_실패하면_401이고_회원을_만들지_않는다() throws Exception {
        String sub = newSub();

        assertThat(login("{\"identityToken\":\"%s\"}".formatted(APPLE.token().subject(sub).audience("com.other.app").build()))
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(login("{\"identityToken\":\"%s\"}".formatted(APPLE.token().subject(sub).issuer("https://evil.example").build()))
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(login("{\"identityToken\":\"%s\",\"nonce\":\"wrong\"}".formatted(APPLE.token().subject(sub).nonce("right").build()))
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(login("{\"identityToken\":\"not-a-jwt\"}").getResponse().getStatus()).isEqualTo(401);

        assertThat(userRepository.findBySocialTypeAndSocialId(SocialType.APPLE, sub)).isEmpty();
    }

    @Test
    void identityToken이_없으면_400() throws Exception {
        assertThat(login("{\"authorizationCode\":\"c\"}").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void Apple_refresh_토큰을_저장했다가_탈퇴하면_폐기한다() throws Exception {
        String sub = newSub();
        doReturn(Optional.of("r.apple-refresh")).when(appleAuthClient)
                .exchangeForRefreshToken("c-ok", "com.hongmap.alimi.preview");
        doReturn(true).when(appleAuthClient).revokeQuietly(anyString(), anyString());

        MvcResult result = login("""
                {"identityToken":"%s","authorizationCode":"c-ok"}
                """.formatted(APPLE.token().subject(sub).audience("com.hongmap.alimi.preview").build()));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        User user = userRepository.findBySocialTypeAndSocialId(SocialType.APPLE, sub).orElseThrow();
        assertThat(user.getAppleRefreshToken()).isEqualTo("r.apple-refresh");
        assertThat(user.getAppleClientId()).isEqualTo("com.hongmap.alimi.preview");

        userService.withdraw(user.getId());

        verify(appleAuthClient).revokeQuietly("r.apple-refresh", "com.hongmap.alimi.preview");
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void 키_설정이_없어도_Apple_회원_탈퇴는_성공한다() throws Exception {
        String sub = newSub();
        login("{\"identityToken\":\"%s\",\"authorizationCode\":\"c\"}".formatted(APPLE.token().subject(sub).build()));
        User user = userRepository.findBySocialTypeAndSocialId(SocialType.APPLE, sub).orElseThrow();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/auth/me")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(user.getId())))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId())).isEmpty();
        verify(appleAuthClient).revokeQuietly(null, null); // 실제 클라이언트: 저장된 토큰 없음 → WARN 후 건너뜀
    }

    @Test
    void 카카오_회원_탈퇴는_Apple을_부르지_않는다() {
        User kakao = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("카카오").build());

        userService.withdraw(kakao.getId());

        verify(appleAuthClient, never()).revokeQuietly(any(), any());
    }
}
