package com.hongmap.hongmapbackend.auth.demo;

import com.hongmap.hongmapbackend.auth.token.RefreshTokenService;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 기본 설정(꺼짐)에서는 POST /auth/demo 가 본문과 상관없이 404 다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoLoginDisabledIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    @Test
    void 꺼져_있으면_404이고_회원을_만들지_않는다() throws Exception {
        mockMvc.perform(post("/auth/demo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/demo").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/demo")).andExpect(status().isNotFound());
        // 열어 둔 것은 POST 뿐 — GET 은 인증 필요(401)
        mockMvc.perform(get("/auth/demo")).andExpect(status().isUnauthorized());

        assertThat(userRepository.findAll().stream().filter(u -> u.getSocialType() == SocialType.DEMO)).isEmpty();
    }

    @Test
    void 켜도_아이디가_비었거나_비밀번호가_12자_미만이면_꺼진_것으로_본다() {
        UserRepository repo = mock(UserRepository.class);
        RefreshTokenService tokens = mock(RefreshTokenService.class);

        assertThat(new DemoLoginService(true, "", "long-enough-password", 10, 10, repo, tokens).isActive()).isFalse();
        assertThat(new DemoLoginService(true, "review", "short-pw-11", 10, 10, repo, tokens).isActive()).isFalse();
        assertThat(new DemoLoginService(true, "review", null, 10, 10, repo, tokens).isActive()).isFalse();
        assertThat(new DemoLoginService(false, "review", "long-enough-password", 10, 10, repo, tokens).isActive()).isFalse();
        assertThat(new DemoLoginService(true, "review", "exactly-12ch", 10, 10, repo, tokens).isActive()).isTrue();
    }
}
