package com.hongmap.hongmapbackend.ask;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /ask/feedback — 비로그인 허용, 본문 검증(400), 빈도 제한(429), 저장. H2 인메모리 DB. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AskFeedbackControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired AskFeedbackLogRepository repository;

    /** 다른 테스트와 한도를 나눠 쓰지 않게 요청마다 다른 IP. */
    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    private static MockHttpServletRequestBuilder ask(String ip, String json) {
        return post("/ask/feedback").contentType(MediaType.APPLICATION_JSON).content(json)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    @Test
    void 비로그인으로_보내면_204_본문_없이_저장된다() throws Exception {
        String question = "휴학 신청 기간 " + UUID.randomUUID();
        mockMvc.perform(ask(randomIp(), """
                        {"question":"%s","outcome":"FLOW","flowId":"leave-of-absence","nodeId":"general","resolved":true}
                        """.formatted(question)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(repository.findAll()).anySatisfy(log -> {
            assertThat(log.getQuestion()).isEqualTo(question);
            assertThat(log.getOutcome()).isEqualTo(AskOutcome.FLOW);
            assertThat(log.getFlowId()).isEqualTo("leave-of-absence");
            assertThat(log.getNodeId()).isEqualTo("general");
            assertThat(log.getResolved()).isTrue();
            assertThat(log.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void 선택_필드_없이도_받는다() throws Exception {
        mockMvc.perform(ask(randomIp(), "{\"question\":\"모르는 질문\",\"outcome\":\"NONE\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 질문이_없거나_공백이면_400() throws Exception {
        mockMvc.perform(ask(randomIp(), "{\"outcome\":\"NONE\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(ask(randomIp(), "{\"question\":\"   \",\"outcome\":\"NONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("질문을 입력해 주세요."));
    }

    @Test
    void 공백을_뺀_질문이_200자를_넘으면_400() throws Exception {
        mockMvc.perform(ask(randomIp(), "{\"question\":\"" + "가".repeat(201) + "\",\"outcome\":\"NONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AskFeedbackService.QUESTION_TOO_LONG_MESSAGE));
        mockMvc.perform(ask(randomIp(), "{\"question\":\"   " + "가".repeat(200) + "   \",\"outcome\":\"NONE\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void outcome_이_없거나_모르는_값이면_400() throws Exception {
        mockMvc.perform(ask(randomIp(), "{\"question\":\"휴학\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(ask(randomIp(), "{\"question\":\"휴학\",\"outcome\":\"MAYBE\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void flowId_nodeId_가_100자를_넘으면_400() throws Exception {
        mockMvc.perform(ask(randomIp(), "{\"question\":\"휴학\",\"outcome\":\"FLOW\",\"flowId\":\"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(ask(randomIp(), "{\"question\":\"휴학\",\"outcome\":\"FLOW\",\"nodeId\":\"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 같은_IP에서_10분에_10번까지_받고_그다음은_429() throws Exception {
        String ip = randomIp();
        // 본문이 잘못된 요청(400)은 세지 않는다.
        mockMvc.perform(ask(ip, "{\"question\":\" \",\"outcome\":\"NONE\"}")).andExpect(status().isBadRequest());
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(ask(ip, "{\"question\":\"질문 " + i + "\",\"outcome\":\"NONE\"}"))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(ask(ip, "{\"question\":\"또 질문\",\"outcome\":\"NONE\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(AskFeedbackRateLimiter.TOO_MANY_MESSAGE));
    }
}
