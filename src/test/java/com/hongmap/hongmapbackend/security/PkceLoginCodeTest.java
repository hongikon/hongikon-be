package com.hongmap.hongmapbackend.security;

import com.hongmap.hongmapbackend.auth.exchange.AuthorizationCodeStore;
import com.hongmap.hongmapbackend.auth.oauth.OAuth2RedirectUriCaptureFilter;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 카카오 로그인 1회용 code 의 PKCE 묶기. 가로챈 code 만으로는(verifier 없이) 토큰을 받을 수 없어야 하고,
 * code_challenge 를 보내지 않는 구버전 앱은 예전처럼 동작해야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PkceLoginCodeTest {

    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk-verifier-123";

    @Autowired MockMvc mockMvc;
    @Autowired AuthorizationCodeStore codeStore;
    @Autowired UserRepository userRepository;

    User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
    }

    private static String challengeOf(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private org.springframework.test.web.servlet.ResultActions exchange(String body) throws Exception {
        return mockMvc.perform(post("/auth/token/exchange").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void verifier_없이는_가로챈_code_로_토큰을_받을_수_없다() throws Exception {
        String code = codeStore.issue(user.getId(), challengeOf(VERIFIER));
        exchange("{\"code\":\"" + code + "\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void 틀린_verifier_면_거부되고_code_는_소비된다() throws Exception {
        String code = codeStore.issue(user.getId(), challengeOf(VERIFIER));
        exchange("{\"code\":\"" + code + "\",\"codeVerifier\":\"" + "x".repeat(43) + "\"}")
                .andExpect(status().isUnauthorized());
        exchange("{\"code\":\"" + code + "\",\"codeVerifier\":\"" + VERIFIER + "\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 맞는_verifier_면_토큰을_발급한다() throws Exception {
        String code = codeStore.issue(user.getId(), challengeOf(VERIFIER));
        exchange("{\"code\":\"" + code + "\",\"codeVerifier\":\"" + VERIFIER + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void challenge_없이_발급된_code_는_예전처럼_교환된다() throws Exception {
        String code = codeStore.issue(user.getId());
        exchange("{\"code\":\"" + code + "\"}").andExpect(status().isOk());
    }

    @Test
    void 로그인_진입_때_보낸_code_challenge_를_세션에_적어_둔다() throws Exception {
        String challenge = challengeOf(VERIFIER);
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/kakao").param("code_challenge", challenge))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getRequest().getSession().getAttribute(OAuth2RedirectUriCaptureFilter.CODE_CHALLENGE_ATTRIBUTE))
                .isEqualTo(challenge);
    }

    @Test
    void 형식이_틀린_code_challenge_는_무시한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/kakao").param("code_challenge", "short"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getRequest().getSession().getAttribute(OAuth2RedirectUriCaptureFilter.CODE_CHALLENGE_ATTRIBUTE))
                .isNull();
    }
}
