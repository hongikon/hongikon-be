package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.push.ExpoPushClient;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제보 댓글 API — 권한(게스트 읽기·로그인 쓰기·관리자), 검증, 빈도 제한, 신고 자동 숨김, 관리자 검토, 삭제 연쇄, 지도 목록 commentCount.
 * H2(MySQL 모드), 엔티티의 @OnDelete 로 FK ON DELETE CASCADE 가 운영 SQL 과 같게 만들어진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportCommentIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired ReportCommentRepository commentRepository;
    @Autowired ReportCommentFlagRepository flagRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ExpoPushClient expoPushClient;

    User author;
    User commenter;
    User admin;
    Building building;

    @BeforeEach
    void setUp() {
        when(expoPushClient.send(anyList())).thenReturn(List.of());
        author = user("홍길동");
        commenter = user("김철수");
        admin = user("관리자");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        building = buildingRepository.save(Building.builder()
                .name("테스트관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    // ---------- 읽기·쓰기 ----------

    @Test
    void 게스트도_목록을_보고_로그인해야_쓴다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        mockMvc.perform(get(comments(report))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(post(comments(report)).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕\"}"))
                .andExpect(status().isUnauthorized());

        write(commenter, report, "  지금 줄 짧아요  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("지금 줄 짧아요"))
                .andExpect(jsonPath("$.authorDisplayName").value("김**"))
                .andExpect(jsonPath("$.authorKey").isString())
                .andExpect(jsonPath("$.isMine").value(true))
                .andExpect(jsonPath("$.authorId").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());

        mockMvc.perform(get(comments(report)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].content").value("지금 줄 짧아요"))
                .andExpect(jsonPath("$.content[0].isMine").value(false))
                .andExpect(content().string(not(containsString("김철수"))));
    }

    @Test
    void 오래된_순_기본이고_latest_와_페이지를_지원한다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        for (int i = 1; i <= 3; i++) {
            write(commenter, report, "댓글" + i).andExpect(status().isCreated());
        }
        mockMvc.perform(get(comments(report)).param("size", "2"))
                .andExpect(jsonPath("$.content[0].content").value("댓글1"))
                .andExpect(jsonPath("$.content[1].content").value("댓글2"))
                .andExpect(jsonPath("$.hasNext").value(true));
        mockMvc.perform(get(comments(report)).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content[0].content").value("댓글3"))
                .andExpect(jsonPath("$.hasNext").value(false));
        mockMvc.perform(get(comments(report)).param("order", "latest").param("size", "1"))
                .andExpect(jsonPath("$.content[0].content").value("댓글3"))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void 공개되지_않은_제보는_읽기_쓰기_모두_404_끝난_제보는_쓰기_409() throws Exception {
        for (ReportStatus status : List.of(ReportStatus.PENDING, ReportStatus.HIDDEN, ReportStatus.REJECTED, ReportStatus.DELETED)) {
            Report report = report(status);
            mockMvc.perform(get(comments(report))).andExpect(status().isNotFound());
            write(commenter, report, "안녕").andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/reports/99999999/comments")).andExpect(status().isNotFound());

        Report ended = reportRepository.save(baseReport(ReportStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusHours(3)).endsAt(LocalDateTime.now().minusMinutes(1)).build());
        write(commenter, ended, "늦었다").andExpect(status().isConflict());
    }

    @Test
    void 빈_댓글과_200자_초과는_400() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        write(commenter, report, "   ").andExpect(status().isBadRequest());
        mockMvc.perform(post(comments(report)).header("Authorization", bearer(commenter))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        write(commenter, report, "가".repeat(201)).andExpect(status().isBadRequest());
        write(commenter, report, " " + "가".repeat(200) + " ").andExpect(status().isCreated());
    }

    @Test
    void 링크_연락처_욕설은_400_이고_저장하지_않는다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        write(commenter, report, "여기 보세요 https://example.com").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("댓글에는 링크를 쓸 수 없어요."));
        write(commenter, report, "naver 닷 com").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("댓글에는 링크를 쓸 수 없어요."));
        write(commenter, report, "010 1234 5678 로 연락 주세요").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("댓글에 연락처나 오픈채팅 주소는 쓸 수 없어요."));
        write(commenter, report, "오픈채팅 들어와요").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("댓글에 연락처나 오픈채팅 주소는 쓸 수 없어요."));
        write(commenter, report, "아 시1발 줄 길다").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("부적절한 표현이 있어 댓글을 올릴 수 없어요. 표현을 바꿔 다시 시도해 주세요."));
        Long root = commentId(write(commenter, report, "3시 발표 끝나고 다시 발급 받으러 가요"));
        // 답글도 같은 필터
        reply(author, report, root, "ㅅ ㅂ").andExpect(status().isBadRequest());

        assertThat(commentRepository.findAll()).filteredOn(c -> c.getReport().getId().equals(report.getId()))
                .extracting(ReportComment::getContent).containsExactly("3시 발표 끝나고 다시 발급 받으러 가요");
        // 막힌 글은 저장되지 않아 빈도 제한(1분 5개)에도 세지 않는다 — 남은 4개를 쓸 수 있다
        for (int i = 0; i < 4; i++) {
            write(commenter, report, "정상 댓글" + i).andExpect(status().isCreated());
        }
    }

    @Test
    void 일분에_5개를_넘으면_429_지워도_다시_쓸_수_없다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long firstId = null;
        for (int i = 0; i < 5; i++) {
            String body = write(commenter, report, "도배" + i).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            if (firstId == null) {
                firstId = com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
            }
        }
        mockMvc.perform(delete(comments(report) + "/" + firstId).header("Authorization", bearer(commenter)))
                .andExpect(status().isNoContent());
        write(commenter, report, "여섯번째").andExpect(status().isTooManyRequests());
        // 다른 사람은 영향 없음
        write(author, report, "작성자 답글").andExpect(status().isCreated());
    }

    @Test
    void 하루_50개를_넘으면_429() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        for (int i = 0; i < 50; i++) {
            commentRepository.save(new ReportComment(report, commenter, "옛 댓글" + i));
        }
        // 방금 쓴 50개를 1분 창 밖(2분 전)으로 옮겨 하루 제한만 걸리게 한다.
        jdbcTemplate.update("UPDATE report_comments SET created_at = ? WHERE user_id = ?",
                LocalDateTime.now().minusMinutes(2), commenter.getId());
        write(commenter, report, "51번째").andExpect(status().isTooManyRequests());
    }

    @Test
    void 내_댓글만_지울_수_있고_지운_댓글은_목록에서_빠진다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long id = commentId(write(commenter, report, "지울 댓글"));

        mockMvc.perform(delete(comments(report) + "/" + id).header("Authorization", bearer(author)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(comments(report) + "/" + id)).andExpect(status().isUnauthorized());
        Report other = report(ReportStatus.ACTIVE);
        mockMvc.perform(delete(comments(other) + "/" + id).header("Authorization", bearer(commenter)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(comments(report) + "/" + id).header("Authorization", bearer(commenter)))
                .andExpect(status().isNoContent());
        assertThat(commentRepository.findById(id)).get()
                .extracting(ReportComment::getStatus).isEqualTo(ReportCommentStatus.DELETED);
        mockMvc.perform(get(comments(report))).andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------- 신고·자동 숨김 ----------

    @Test
    void 신고_3개면_자동_숨김_중복_본인_잘못된_사유는_거절() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long id = commentId(write(commenter, report, "광고 댓글"));

        flag(commenter, report, id, "SPAM").andExpect(status().isBadRequest());
        flag(author, report, id, "NOPE").andExpect(status().isBadRequest());

        flag(author, report, id, "SPAM").andExpect(status().isCreated())
                .andExpect(jsonPath("$.flagCount").value(1)).andExpect(jsonPath("$.hidden").value(false));
        flag(author, report, id, "SPAM").andExpect(status().isConflict());
        flag(user("신고2"), report, id, "PRIVACY").andExpect(status().isCreated());
        flag(user("신고3"), report, id, "INAPPROPRIATE").andExpect(status().isCreated())
                .andExpect(jsonPath("$.flagCount").value(3)).andExpect(jsonPath("$.hidden").value(true));

        mockMvc.perform(get(comments(report))).andExpect(jsonPath("$.totalElements").value(0));
        // 숨겨진 댓글은 더 신고할 수 없다
        flag(user("신고4"), report, id, "SPAM").andExpect(status().isNotFound());
    }

    @Test
    void 관리자가_복원한_댓글은_복원_뒤_신고만_센다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long id = commentId(write(commenter, report, "애매한 댓글"));
        for (int i = 0; i < 3; i++) {
            flag(user("신고" + i), report, id, "ETC").andExpect(status().isCreated());
        }
        moderate(id, "VISIBLE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VISIBLE"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        // 검토 시각과 같은 초에 들어온 신고가 섞이지 않게 이전 신고를 과거로 옮긴다.
        jdbcTemplate.update("UPDATE report_comment_flags SET created_at = ? WHERE comment_id = ?",
                LocalDateTime.now().minusMinutes(5), id);

        flag(user("새신고1"), report, id, "SPAM").andExpect(jsonPath("$.hidden").value(false));
        flag(user("새신고2"), report, id, "SPAM").andExpect(jsonPath("$.hidden").value(false));
        flag(user("새신고3"), report, id, "SPAM").andExpect(jsonPath("$.hidden").value(true));
    }

    // ---------- 관리자 ----------

    @Test
    void 관리자는_숨김_삭제_포함_전체를_보고_상태를_바꾼다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long keep = commentId(write(commenter, report, "좋은 정보"));
        Long bad = commentId(write(commenter, report, "나쁜 댓글"));
        flag(author, report, bad, "INAPPROPRIATE").andExpect(status().isCreated());

        mockMvc.perform(get("/admin/reports/" + report.getId() + "/comments").header("Authorization", bearer(commenter)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/admin/comments/" + bad).header("Authorization", bearer(commenter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isForbidden());

        moderate(bad, "HIDDEN").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HIDDEN"));
        mockMvc.perform(get(comments(report))).andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/admin/reports/" + report.getId() + "/comments").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(2))
                .andExpect(jsonPath("$.comments[0].id").value(keep))
                .andExpect(jsonPath("$.comments[1].status").value("HIDDEN"))
                .andExpect(jsonPath("$.comments[1].authorId").value(commenter.getId()))
                .andExpect(jsonPath("$.comments[1].authorNickname").value("김철수"))
                .andExpect(jsonPath("$.comments[1].flagCount").value(1))
                .andExpect(jsonPath("$.comments[1].flagReasons.INAPPROPRIATE").value(1));

        moderate(bad, "DELETED").andExpect(jsonPath("$.status").value("DELETED"));
        moderate(bad, "PENDING").andExpect(status().isBadRequest());
        moderate(99999999L, "HIDDEN").andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/reports/99999999/comments").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    // ---------- 삭제 연쇄·목록 ----------

    @Test
    void 제보를_지우면_댓글과_신고도_지워진다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long id = commentId(write(commenter, report, "곧 사라질 댓글"));
        flag(author, report, id, "SPAM").andExpect(status().isCreated());

        mockMvc.perform(delete("/reports/" + report.getId()).header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());
        assertThat(commentRepository.findById(id)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_comment_flags WHERE comment_id = ?", Long.class, id)).isZero();
    }

    @Test
    void 탈퇴하면_내_댓글과_내_신고_내_제보에_달린_댓글이_지워진다() throws Exception {
        User leaver = user("탈퇴자");
        Report othersReport = report(ReportStatus.ACTIVE);
        Report leaversReport = reportRepository.save(baseReport(ReportStatus.ACTIVE).user(leaver).build());

        Long mine = commentId(write(leaver, othersReport, "내 댓글"));
        Long survivor = commentId(write(commenter, othersReport, "남는 댓글"));
        Long onMyReport = commentId(write(commenter, leaversReport, "탈퇴자 제보의 댓글"));
        flag(leaver, othersReport, survivor, "SPAM").andExpect(status().isCreated());

        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(leaver))).andExpect(status().isNoContent());

        assertThat(commentRepository.findById(mine)).isEmpty();
        assertThat(commentRepository.findById(onMyReport)).isEmpty();
        assertThat(commentRepository.findById(survivor)).isPresent();
        assertThat(flagRepository.countByComment_Id(survivor)).isZero();
    }

    @Test
    void 지도_목록에_공개_댓글_수가_붙는다() throws Exception {
        Report withComments = report(ReportStatus.ACTIVE);
        Report without = report(ReportStatus.ACTIVE);
        write(commenter, withComments, "하나").andExpect(status().isCreated());
        Long hidden = commentId(write(commenter, withComments, "둘"));
        write(author, withComments, "셋").andExpect(status().isCreated());
        moderate(hidden, "HIDDEN").andExpect(status().isOk());

        String body = mockMvc.perform(get("/reports").param("buildingId", building.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = com.jayway.jsonpath.JsonPath.parse(body);
        assertThat(json.read("$.reports[?(@.id == " + withComments.getId() + ")].commentCount", List.class))
                .containsExactly(2);
        assertThat(json.read("$.reports[?(@.id == " + without.getId() + ")].commentCount", List.class))
                .containsExactly(0);
    }

    // ---------- 답글 ----------

    @Test
    void 답글은_한_단계만_답글에_답하면_같은_최상위_댓글에_붙는다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long root = commentId(write(commenter, report, "줄 길어요?"));
        Long reply = commentId(reply(author, report, root, "지금 5명이요"));
        // 답글에 답하면 최상위 댓글로 다시 연결
        reply(commenter, report, reply, "감사합니다").andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(root));
        // 다른 제보의 댓글을 부모로 주면 404
        Report other = report(ReportStatus.ACTIVE);
        reply(commenter, other, root, "엉뚱한 제보").andExpect(status().isNotFound());

        mockMvc.perform(get(comments(report)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.commentCount").value(3))
                .andExpect(jsonPath("$.content[0].id").value(root))
                .andExpect(jsonPath("$.content[0].replyCount").value(2))
                .andExpect(jsonPath("$.content[0].replies.length()").value(2))
                .andExpect(jsonPath("$.content[0].replies[0].id").value(reply))
                .andExpect(jsonPath("$.content[0].replies[0].parentId").value(root))
                .andExpect(jsonPath("$.content[0].replies[1].content").value("감사합니다"));
    }

    @Test
    void 목록엔_답글_3개까지_나머지는_replies_로_본다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long root = commentId(write(commenter, report, "질문"));
        for (int i = 1; i <= 5; i++) {
            commentRepository.save(new ReportComment(report, author, "답" + i, commentRepository.findById(root).orElseThrow()));
        }
        mockMvc.perform(get(comments(report)))
                .andExpect(jsonPath("$.content[0].replyCount").value(5))
                .andExpect(jsonPath("$.content[0].replies.length()").value(3))
                .andExpect(jsonPath("$.content[0].replies[2].content").value("답3"));
        mockMvc.perform(get(comments(report) + "/" + root + "/replies").param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].content").value("답4"))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void 답글이_있는_댓글을_지우면_자리만_남고_답글이_없으면_사라진다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long withReply = commentId(write(commenter, report, "지울 질문"));
        Long reply = commentId(reply(author, report, withReply, "답변"));
        Long lonely = commentId(write(commenter, report, "혼자 댓글"));

        mockMvc.perform(delete(comments(report) + "/" + withReply).header("Authorization", bearer(commenter)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(comments(report) + "/" + lonely).header("Authorization", bearer(commenter)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(comments(report)).header("Authorization", bearer(commenter)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.commentCount").value(1))
                .andExpect(jsonPath("$.content[0].id").value(withReply))
                .andExpect(jsonPath("$.content[0].placeholder").value("DELETED"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.content[0].authorDisplayName").doesNotExist())
                .andExpect(jsonPath("$.content[0].authorKey").doesNotExist())
                .andExpect(jsonPath("$.content[0].replies[0].id").value(reply));
        // 지워진 댓글에는 답글을 달 수 없다
        reply(author, report, withReply, "늦은 답").andExpect(status().isNotFound());

        // 마지막 답글까지 지우면 자리도 사라진다
        mockMvc.perform(delete(comments(report) + "/" + reply).header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(comments(report))).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 제보가_지워지면_답글도_지워진다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long root = commentId(write(commenter, report, "질문"));
        Long reply = commentId(reply(author, report, root, "답"));
        mockMvc.perform(delete("/reports/" + report.getId()).header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());
        assertThat(commentRepository.findById(reply)).isEmpty();
    }

    // ---------- helpers ----------

    private User user(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private Report.ReportBuilder baseReport(ReportStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return Report.builder()
                .user(author).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .status(status);
    }

    private Report report(ReportStatus status) {
        return reportRepository.save(baseReport(status).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private static String comments(Report report) {
        return "/reports/" + report.getId() + "/comments";
    }

    private ResultActions write(User user, Report report, String content) throws Exception {
        return mockMvc.perform(post(comments(report)).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + escape(content) + "\"}"));
    }

    private ResultActions reply(User user, Report report, Long parentId, String content) throws Exception {
        return mockMvc.perform(post(comments(report)).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + escape(content) + "\",\"parentId\":" + parentId + "}"));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ResultActions flag(User user, Report report, Long commentId, String reason) throws Exception {
        return mockMvc.perform(post(comments(report) + "/" + commentId + "/flags").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }

    private ResultActions moderate(Long commentId, String status) throws Exception {
        return mockMvc.perform(patch("/admin/comments/" + commentId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private static Long commentId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);
    }
}
