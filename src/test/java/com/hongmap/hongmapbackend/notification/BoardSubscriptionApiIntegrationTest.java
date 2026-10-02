package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게시판 구독 API(/users/me/subscriptions) — 조회·upsert·멱등 삭제·검증·인증, 회원탈퇴 시 정리.
 * H2 인메모리 DB(application-test.properties).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoardSubscriptionApiIntegrationTest {

    private static final String BASE = "/users/me/subscriptions";

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired UserBoardSubscriptionRepository subscriptionRepository;

    User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private String body(boolean alertEnabled) {
        return "{\"alertEnabled\":" + alertEnabled + "}";
    }

    @Test
    void 구독이_없으면_빈_목록() throws Exception {
        mockMvc.perform(get(BASE).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions").isArray())
                .andExpect(jsonPath("$.subscriptions.length()").value(0));
    }

    @Test
    void PUT은_없으면_구독을_만들고_목록에_보인다() throws Exception {
        mockMvc.perform(put(BASE + "/{sourceId}", "컴퓨터공학과").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value("컴퓨터공학과"))
                .andExpect(jsonPath("$.alertEnabled").value(true))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));

        mockMvc.perform(get(BASE).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.length()").value(1))
                .andExpect(jsonPath("$.subscriptions[0].sourceId").value("컴퓨터공학과"))
                .andExpect(jsonPath("$.subscriptions[0].alertEnabled").value(true))
                .andExpect(jsonPath("$.subscriptions[0].createdAt").isNotEmpty());
    }

    @Test
    void PUT을_다시_하면_알림만_바뀌고_구독은_하나로_유지된다() throws Exception {
        mockMvc.perform(put(BASE + "/{sourceId}", "학사").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isOk());
        mockMvc.perform(put(BASE + "/{sourceId}", "학사").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value("학사"))
                .andExpect(jsonPath("$.alertEnabled").value(false));

        assertThat(subscriptionRepository.findByUser_IdOrderByCreatedAtAscIdAsc(user.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.isAlertEnabled()).isFalse());
    }

    @Test
    void DELETE는_멱등이다() throws Exception {
        mockMvc.perform(put(BASE + "/{sourceId}", "장학").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(BASE + "/{sourceId}", "장학").header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(BASE + "/{sourceId}", "장학").header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findByUser_IdAndSourceId(user.getId(), "장학")).isEmpty();
    }

    @Test
    void 알_수_없는_게시판은_400() throws Exception {
        mockMvc.perform(put(BASE + "/{sourceId}", "없는게시판").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("존재하지 않는 게시판입니다: 없는게시판"));
        mockMvc.perform(delete(BASE + "/{sourceId}", "없는게시판").header("Authorization", bearer(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alertEnabled가_없으면_400() throws Exception {
        mockMvc.perform(put(BASE + "/{sourceId}", "학사").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE + "/{sourceId}", "학사")
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE + "/{sourceId}", "학사")).andExpect(status().isUnauthorized());
    }

    @Test
    void URL_인코딩된_한글_sourceId를_받는다() throws Exception {
        // 프론트가 encodeURIComponent로 보낸 경로 그대로.
        String encoded = "/users/me/subscriptions/%EA%B5%90%EC%88%98%ED%95%99%EC%8A%B5%EC%A7%80%EC%9B%90"; // 교수학습지원
        mockMvc.perform(put(java.net.URI.create(encoded)).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value("교수학습지원"));
        mockMvc.perform(delete(java.net.URI.create(encoded)).header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());
    }

    @Test
    void 구독_개수_상한을_넘으면_400() throws Exception {
        // 상한(100)이 실제 게시판 수보다 커서, 상한까지 직접 채워 놓고 확인한다.
        for (int i = 0; i < BoardSubscriptionService.MAX_SUBSCRIPTIONS_PER_USER; i++) {
            subscriptionRepository.save(UserBoardSubscription.builder()
                    .user(user).sourceId("채움-" + i).alertEnabled(true).build());
        }
        mockMvc.perform(put(BASE + "/{sourceId}", "학사").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("게시판은 최대 100개까지 구독할 수 있습니다."));
    }

    @Test
    void 회원탈퇴하면_구독도_지워진다() throws Exception {
        mockMvc.perform(put(BASE + "/{sourceId}", "학사").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findByUser_IdOrderByCreatedAtAscIdAsc(user.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }
}
