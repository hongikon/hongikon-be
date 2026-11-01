package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 앱 '물어보기' 탭(BETA) — 로그인 사용자는 암호키 없이 묻고, 게스트는 403, 계정 하루 한도를 넘으면 429. */
@SpringBootTest(properties = {"ask.public.enabled=true", "ask.public.user-daily-limit=2", "ask.public.daily-limit=150"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AskPublicRouteIntegrationTest {

    private static final String NODE = "leave-of-absence/tuition";

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @MockitoBean AskLlmClient llmClient;
    @MockitoBean AskRetriever retriever;

    private String bearer() {
        User user = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private void answers() {
        when(llmClient.classify(anyString(), any())).thenReturn(new AskLlmClient.Classification(AskScope.SCHOOL, List.of(NODE)));
        when(llmClient.answer(eq(List.of(NODE)), anyString(), any()))
                .thenReturn(new AskLlmClient.Answer("일반휴학은 돌려받지 않아요.", true, List.of(NODE)));
    }

    @Test
    void 로그인하면_암호키_없이_묻고_한도를_넘으면_429() throws Exception {
        answers();
        String token = bearer();
        String body = "{\"question\":\"휴학하면 등록금 돌려받아?\"}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/ask/route").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value("일반휴학은 돌려받지 않아요."));
        }
        mockMvc.perform(post("/ask/route").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/ask/status").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void 게스트는_403이고_LLM을_부르지_않는다() throws Exception {
        mockMvc.perform(post("/ask/route").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"휴학 어떻게 해요\"}"))
                .andExpect(status().isForbidden());
        verify(llmClient, never()).classify(anyString(), any());
        mockMvc.perform(get("/ask/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(false));
    }

    @Test
    void 운영진_암호키는_그대로_쓴다() throws Exception {
        answers();
        mockMvc.perform(post("/ask/route").header(AskRouteController.DEV_KEY_HEADER, "test-ask-dev-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"휴학하면 등록금 돌려받아?\"}"))
                .andExpect(status().isOk());
    }
}
