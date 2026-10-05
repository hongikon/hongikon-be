package com.hongmap.hongmapbackend.feedback;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /feedback 도배 방지 — 접속 IP·사용자마다 10분에 5건(기본값), 넘으면 429. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackRateLimitIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;

    /** 다른 테스트(127.0.0.1)와 한도를 나눠 쓰지 않게 테스트마다 다른 IP. */
    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    private MockHttpServletRequestBuilder feedback(String ip, String content) {
        return post("/feedback").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    @Test
    void 게스트는_같은_IP에서_10분에_5건까지_보내고_그다음은_429() throws Exception {
        String ip = randomIp();
        // 본문이 잘못된 요청(400)은 세지 않는다.
        mockMvc.perform(feedback(ip, "  ")).andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(feedback(ip, "문의 " + i)).andExpect(status().isCreated());
        }
        mockMvc.perform(feedback(ip, "또 문의")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(FeedbackRateLimiter.TOO_MANY_MESSAGE));
        // 다른 IP 는 영향 없음
        mockMvc.perform(feedback(randomIp(), "다른 곳")).andExpect(status().isCreated());
    }

    @Test
    void 로그인_사용자는_IP를_바꿔도_사용자_기준으로_막힌다() throws Exception {
        User user = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("문의러").build());
        String bearer = "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(feedback(randomIp(), "문의 " + i).header("Authorization", bearer))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(feedback(randomIp(), "또 문의").header("Authorization", bearer))
                .andExpect(status().isTooManyRequests());
    }
}
