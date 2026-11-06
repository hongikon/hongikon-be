package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.feedback.FeedbackRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 '물어보기 피드백'(11-06): 해결 안 됨 이유 · 같은 응답 잇기 · 문의 연결 · 확인 표시. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AskFeedbackAdminIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired AskConversationLogRepository conversationLogs;
    @Autowired AskFeedbackLogRepository feedbackLogs;
    @Autowired AskConversationRecorder recorder;
    @Autowired FeedbackRepository feedbackRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    User admin;
    User student;
    String tail;

    @BeforeEach
    void setUp() {
        tail = UUID.randomUUID().toString().substring(0, 8);
        admin = userRepository.save(User.builder().socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO)
                .nickname("운영자" + tail).build());
        student = userRepository.save(User.builder().socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO)
                .nickname("학생" + tail).build());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private AskConversationLog routeLog(String question) {
        return conversationLogs.save(AskConversationLog.builder().conversationId("conv-" + tail).turn(1).question(question)
                .answer("전과는 2학년 33학점 이상이에요.").outcome(AskConversationLog.Outcome.ANSWERED)
                .nodeIds("change-major/start").referenceTitles("학사 FAQ 전과\n학사내규 제7장").build());
    }

    private String q(String jsonPathFilterQuestion) {
        return "$.items[?(@.question == '" + jsonPathFilterQuestion + "')]";
    }

    @Test
    void 해결_안_됨_이유를_붙이고_아니요만_다시_와도_이유를_지우지_않는다() {
        AskConversationLog row = routeLog("전과 학점 " + tail);
        assertThat(recorder.resolve(row.getId(), "conv-" + tail, false, AskFeedbackReason.OUTDATED)).isTrue();
        assertThat(recorder.resolve(row.getId(), "conv-" + tail, false, null)).isTrue();
        assertThat(conversationLogs.findById(row.getId()).orElseThrow().getReason()).isEqualTo(AskFeedbackReason.OUTDATED);
        assertThat(recorder.resolve(row.getId(), "다른-대화-" + tail, false, AskFeedbackReason.WRONG)).isFalse();
        assertThat(recorder.resolve(row.getId(), "conv-" + tail, true, null)).isTrue();
        assertThat(conversationLogs.findById(row.getId()).orElseThrow().getReason()).isNull();
    }

    @Test
    void 관리자_목록은_서버답과_흐름답을_합치고_문의를_붙이며_확인하면_빠진다() throws Exception {
        String routeQ = "전과 몇 학점 " + tail;
        AskConversationLog row = routeLog(routeQ);
        recorder.resolve(row.getId(), "conv-" + tail, false, AskFeedbackReason.WRONG);

        // 앱 안내 흐름 답: '아니요' 먼저, 같은 clientId 로 이유를 골라 한 번 더 — 한 행만 남는다.
        String flowQ = "휴학 신청 " + tail;
        String clientId = "c-" + tail + "-flow";
        String body = "{\"question\":\"" + flowQ + "\",\"outcome\":\"FLOW\",\"flowId\":\"leave-of-absence\",\"nodeId\":\"general\","
                + "\"resolved\":false,\"clientId\":\"" + clientId + "\"%s}";
        mockMvc.perform(post("/ask/feedback").contentType(MediaType.APPLICATION_JSON).content(body.formatted("")))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/ask/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(",\"reason\":\"not_enough\"")))
                .andExpect(status().isNoContent());
        assertThat(feedbackLogs.findAll().stream().filter(l -> clientId.equals(l.getClientId())).count()).isEqualTo(1);

        // '운영진에게 물어보기' 문의 — 서버 답에 잇는다.
        mockMvc.perform(post("/feedback").header("Authorization", bearer(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"편입생도 되나요 " + tail + "\",\"askRef\":\"route:" + row.getId() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/admin/ask/feedback").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(q(routeQ) + ".source", contains("ROUTE")))
                .andExpect(jsonPath(q(routeQ) + ".reason", contains("WRONG")))
                .andExpect(jsonPath(q(routeQ) + ".references[1]", contains("학사내규 제7장")))
                .andExpect(jsonPath(q(routeQ) + ".handoffs[0].content", contains("편입생도 되나요 " + tail)))
                .andExpect(jsonPath(q(flowQ) + ".source", contains("FLOW")))
                .andExpect(jsonPath(q(flowQ) + ".reason", contains("NOT_ENOUGH")))
                .andExpect(jsonPath(q(flowQ) + ".flowId", contains("leave-of-absence")));

        mockMvc.perform(patch("/admin/ask/feedback/ROUTE/" + row.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reviewed\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/admin/ask/feedback").header("Authorization", bearer(admin)))
                .andExpect(jsonPath(q(routeQ), empty()));
        mockMvc.perform(get("/admin/ask/feedback").param("status", "REVIEWED").header("Authorization", bearer(admin)))
                .andExpect(jsonPath(q(routeQ), hasSize(1)));
    }

    @Test
    void 관리자가_아니면_못_보고_잘못된_askRef_는_400() throws Exception {
        mockMvc.perform(get("/admin/ask/feedback").header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"문의 " + tail + "\",\"askRef\":\"../etc\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/admin/ask/feedback/OTHER/1").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reviewed\":true}"))
                .andExpect(status().isBadRequest());
    }
}
