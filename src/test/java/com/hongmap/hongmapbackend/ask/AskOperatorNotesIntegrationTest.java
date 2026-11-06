package com.hongmap.hongmapbackend.ask;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 운영진 확인 안내(11-06): 등록·개인 정보 거절·끄기, 질문에 맞춰 고르기, 주간 요약 묶기. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AskOperatorNotesIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired AskOperatorNotes notes;
    @Autowired AskRetriever retriever;
    @Autowired AskRouteService routeService;
    @Autowired AskConversationLogRepository conversationLogs;
    @Autowired JdbcTemplate jdbcTemplate;

    User admin;
    String tail;

    @BeforeEach
    void setUp() {
        tail = UUID.randomUUID().toString().substring(0, 8);
        admin = userRepository.save(User.builder().socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO)
                .nickname("운영자" + tail).build());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.generateAccessToken(admin.getId());
    }

    private long create(String question, String answer) throws Exception {
        String body = mockMvc.perform(post("/admin/ask/notes").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\",\"answer\":\"" + answer + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void 등록한_안내를_비슷한_질문에_고르고_끄면_더는_고르지_않는다() throws Exception {
        String topic = "멀미실" + tail.substring(0, 3);
        long id = create(topic + " 프린터 몇 장까지 뽑을 수 있어요", topic + " 프린터는 한 학기 800매까지예요.");
        String pageId = AskOperatorNotes.PREFIX + id;

        assertThat(notes.match(topic + " 프린터 몇 장 뽑을 수 있어?", null, 2)).contains(pageId);
        assertThat(notes.match("기숙사 신청 기간", null, 2)).doesNotContain(pageId);
        assertThat(retriever.page(pageId).text()).contains("800매");
        assertThat(retriever.page(pageId).url()).isNull();
        assertThat(routeService.withOperatorNotes(List.of("page:reg-naegyu-ch07"), topic + " 프린터 몇 장", null))
                .containsExactly(pageId, "page:reg-naegyu-ch07");

        mockMvc.perform(patch("/admin/ask/notes/" + id).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        assertThat(notes.match(topic + " 프린터 몇 장 뽑을 수 있어?", null, 2)).doesNotContain(pageId);
        mockMvc.perform(get("/admin/ask/notes").header("Authorization", bearer()))
                .andExpect(jsonPath("$.notes[*].id", hasItem((int) id)));
    }

    @Test
    void 학생_개인_정보가_있으면_저장하지_않는다() throws Exception {
        mockMvc.perform(post("/admin/ask/notes").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"휴학 문의\",\"answer\":\"010-1234-5678 로 연락 주세요\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("휴대전화 번호")));
        // 학교 부서 전화는 안내 정보라 받는다.
        create("학사지원팀 전화 " + tail, "학사지원팀은 02-320-1037 이에요.");
    }

    @Test
    void 주간_요약은_같은_안내끼리와_비슷한_질문끼리_묶는다() throws Exception {
        String conv = "conv-" + tail;
        for (String q : List.of("전과 학점 " + tail, "전과 몇 학점 " + tail)) {
            AskConversationLog row = conversationLogs.save(AskConversationLog.builder().conversationId(conv).turn(1).question(q)
                    .outcome(AskConversationLog.Outcome.ANSWERED).nodeIds("change-major/start").build());
            row.resolve(false, AskFeedbackReason.OUTDATED);
            conversationLogs.save(row);
        }
        for (String q : List.of("멀미실 와이파이 " + tail, "멀미실 와이파이 안됨 " + tail)) {
            AskConversationLog row = conversationLogs.save(AskConversationLog.builder().conversationId(conv).turn(1).question(q)
                    .outcome(AskConversationLog.Outcome.NO_ANSWER).build());
            row.resolve(false, null);
            conversationLogs.save(row);
        }
        mockMvc.perform(get("/admin/ask/feedback/report").param("days", "7").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[?(@.key == 'change-major/start')].count", hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.groups[?(@.key == 'change-major/start')].reasons.OUTDATED", hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(2))))
                // 대표 질문은 가장 최근 것 — 두 질문 중 어느 쪽이든 한 묶음에 2건.
                .andExpect(jsonPath("$.groups[?(@.kind == 'UNMATCHED' && @.key =~ /멀미실 와이파이.*" + tail + "/)].count", hasItem(2)));
    }
}
