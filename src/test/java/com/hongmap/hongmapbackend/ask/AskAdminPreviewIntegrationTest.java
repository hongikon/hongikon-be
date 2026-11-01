package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.hongmap.hongmapbackend.user.UserRole;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 스위치(ask.public.enabled)가 꺼져 있어도 운영진은 앱 탭에서 묻고, 일반 회원·게스트는 막힌다(공개 전 테스트 앱 확인). */
@SpringBootTest(properties = {"ask.public.enabled=false", "ask.public.admin-preview=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AskAdminPreviewIntegrationTest {

    private static final String NODE = "leave-of-absence/tuition";
    private static final String BODY = "{\"question\":\"휴학하면 등록금 돌려받아?\"}";

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @MockitoBean AskLlmClient llmClient;
    @MockitoBean AskRetriever retriever;

    private String bearer(boolean admin) {
        User user = User.builder().socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("회원").build();
        if (admin) {
            user.changeRole(UserRole.ADMIN);
        }
        return "Bearer " + jwtTokenProvider.generateAccessToken(userRepository.save(user).getId());
    }

    @Test
    void 운영진은_스위치가_꺼져도_묻는다() throws Exception {
        when(llmClient.classify(anyString(), any())).thenReturn(new AskLlmClient.Classification(AskScope.SCHOOL, List.of(NODE)));
        when(llmClient.answer(eq(List.of(NODE)), anyString(), any()))
                .thenReturn(new AskLlmClient.Answer("일반휴학은 돌려받지 않아요.", true, List.of(NODE)));
        String token = bearer(true);
        mockMvc.perform(post("/ask/route").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        mockMvc.perform(get("/ask/status").header("Authorization", token))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void 일반_회원과_게스트는_스위치가_꺼져_있으면_막힌다() throws Exception {
        String token = bearer(false);
        mockMvc.perform(post("/ask/route").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/ask/status").header("Authorization", token))
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(get("/ask/status")).andExpect(jsonPath("$.enabled").value(false));
    }
}
