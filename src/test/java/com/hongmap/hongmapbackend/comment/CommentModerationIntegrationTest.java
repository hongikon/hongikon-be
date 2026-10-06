package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.push.ExpoPushClient;
import com.hongmap.hongmapbackend.push.ExpoPushMessage;
import com.hongmap.hongmapbackend.push.ExpoPushTicket;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
 * 댓글 신고 검토 보강(2026-10-06) — 신고된 댓글 목록·대시보드 수, 검토 완료(유지), 작성자 조치 알림(사유·14일 이의 제기),
 * 신고 빈도 제한(429), flaggedByMe, 비공개 제보 댓글 신고 404·사유 값 되비추지 않기.
 * 공유 H2 라 다른 테스트의 신고 댓글이 목록·수에 섞일 수 있어, 내 댓글 id 로 거르고 수는 전후 차이로 본다.
 * Expo API 는 ExpoPushClient 를 모킹하고 이 테스트의 토큰(run 접두어)만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentModerationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired ReportCommentRepository commentRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired UserNotificationSettingRepository settingRepository;
    @Autowired CommentFlagLimiter flagLimiter;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ExpoPushClient expoPushClient;

    String run;
    List<ExpoPushMessage> sent;
    User reportAuthor;
    User commenter;
    User admin;
    Building building;

    @BeforeEach
    void setUp() {
        flagLimiter.resetForTest();
        run = UUID.randomUUID().toString().substring(0, 8);
        sent = Collections.synchronizedList(new ArrayList<>());
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            sent.addAll(batch);
            return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
        });
        reportAuthor = user("홍길동");
        commenter = user("김철수");
        admin = user("관리자");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        building = buildingRepository.save(Building.builder()
                .name("테스트관-" + run)
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    @AfterEach
    void deactivateDevices() {
        jdbcTemplate.update("UPDATE user_devices SET is_active = false WHERE push_token LIKE ?", "%[" + run + "-%");
    }

    // ---------- 신고 API ----------

    @Test
    void 비공개_제보의_댓글은_신고할_수_없고_잘못된_사유는_되비추지_않는다() throws Exception {
        Report report = report();
        Long id = comment(report, commenter, "숨겨질 제보의 댓글");
        jdbcTemplate.update("UPDATE reports SET status = 'HIDDEN' WHERE id = ?", report.getId());

        flag(user("신고자"), report, id, "SPAM").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 제보입니다."));

        jdbcTemplate.update("UPDATE reports SET status = 'ACTIVE' WHERE id = ?", report.getId());
        flag(user("신고자2"), report, id, "<script>alert(1)</script>").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("존재하지 않는 신고 사유입니다."))
                .andExpect(content().string(not(containsString("script"))));
    }

    @Test
    void 신고는_10분에_10번까지_넘으면_429_거절된_요청은_세지_않는다() throws Exception {
        Report report = report();
        User flagger = user("신고왕");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            ids.add(comment(report, user("작성" + i), "댓글" + i));
        }
        // 잘못된 사유·중복(409)은 한도를 깎지 않는다.
        flag(flagger, report, ids.get(0), "NOPE").andExpect(status().isBadRequest());
        for (int i = 0; i < 10; i++) {
            flag(flagger, report, ids.get(i), "SPAM").andExpect(status().isCreated());
        }
        flag(flagger, report, ids.get(0), "SPAM").andExpect(status().isConflict());
        flag(flagger, report, ids.get(10), "SPAM").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("신고를 너무 자주 하고 있어요. 잠시 뒤에 다시 시도해 주세요."));
        // 다른 사람은 영향 없음
        flag(user("다른 사람"), report, ids.get(10), "SPAM").andExpect(status().isCreated());
    }

    @Test
    void 목록의_flaggedByMe_는_내가_신고한_댓글과_답글에만_true() throws Exception {
        Report report = report();
        Long root = comment(report, commenter, "질문");
        Long reply = commentRepository.save(new ReportComment(report, reportAuthor, "답",
                commentRepository.findById(root).orElseThrow())).getId();
        Long other = comment(report, commenter, "다른 댓글");
        User flagger = user("신고자");
        flag(flagger, report, root, "SPAM").andExpect(status().isCreated());
        flag(flagger, report, reply, "ETC").andExpect(status().isCreated());

        mockMvc.perform(get(comments(report)).header("Authorization", bearer(flagger)))
                .andExpect(jsonPath("$.content[0].id").value(root))
                .andExpect(jsonPath("$.content[0].flaggedByMe").value(true))
                .andExpect(jsonPath("$.content[0].replies[0].flaggedByMe").value(true))
                .andExpect(jsonPath("$.content[1].id").value(other))
                .andExpect(jsonPath("$.content[1].flaggedByMe").value(false));
        mockMvc.perform(get(comments(report) + "/" + root + "/replies").header("Authorization", bearer(flagger)))
                .andExpect(jsonPath("$.content[0].flaggedByMe").value(true));
        // 다른 사람·게스트에게는 false
        mockMvc.perform(get(comments(report)).header("Authorization", bearer(commenter)))
                .andExpect(jsonPath("$.content[0].flaggedByMe").value(false));
        mockMvc.perform(get(comments(report))).andExpect(jsonPath("$.content[0].flaggedByMe").value(false));
    }

    // ---------- 신고된 댓글 목록·대시보드 ----------

    @Test
    void 신고된_댓글_목록은_검토_대기만_최근_신고_순으로_보이고_대시보드_수와_맞다() throws Exception {
        long before = overviewFlaggedPending();
        Report report = report();
        Long older = comment(report, commenter, "먼저 신고된 댓글");
        Long autoHidden = comment(report, commenter, "자동 숨김될 댓글");
        Long kept = comment(report, commenter, "검토 완료로 남길 댓글");
        Long selfDeleted = comment(report, commenter, "작성자가 지울 댓글");
        Long clean = comment(report, commenter, "신고 없는 댓글");
        device(commenter);

        flag(user("a"), report, older, "SPAM").andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE report_comment_flags SET created_at = ? WHERE comment_id = ?",
                LocalDateTime.now().minusMinutes(5), older);
        for (int i = 0; i < 3; i++) {
            flag(user("b" + i), report, autoHidden, i == 0 ? "PRIVACY" : "INAPPROPRIATE").andExpect(status().isCreated());
        }
        flag(user("c"), report, kept, "ETC").andExpect(status().isCreated());
        flag(user("d"), report, selfDeleted, "SPAM").andExpect(status().isCreated());
        mockMvc.perform(delete(comments(report) + "/" + selfDeleted).header("Authorization", bearer(commenter)))
                .andExpect(status().isNoContent());
        assertThat(overviewFlaggedPending()).isEqualTo(before + 3);

        // 검토 완료(유지): 공개 중인 댓글에 VISIBLE — 상태는 그대로, 검토 시각이 남아 목록에서 빠진다. 작성자 알림 없음.
        moderate(kept, "VISIBLE", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VISIBLE"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        assertThat(overviewFlaggedPending()).isEqualTo(before + 2);

        String body = mockMvc.perform(get("/admin/comments").param("filter", "flagged").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("김철수"))))
                .andReturn().getResponse().getContentAsString();
        var json = JsonPath.parse(body);
        List<Integer> mineIds = json.read("$.comments[?(@.reportId == " + report.getId() + ")].id", List.class);
        assertThat(mineIds).containsExactly(autoHidden.intValue(), older.intValue());
        assertThat(json.read("$.total", Integer.class)).isGreaterThanOrEqualTo(2);
        String hiddenRow = "$.comments[?(@.id == " + autoHidden + ")]";
        assertThat(json.read(hiddenRow + ".status", List.class)).containsExactly("HIDDEN");
        assertThat(json.read(hiddenRow + ".reportTitle", List.class)).containsExactly("붕어빵 트럭");
        assertThat(json.read(hiddenRow + ".reportStatus", List.class)).containsExactly("ACTIVE");
        assertThat(json.read(hiddenRow + ".flagCount", List.class)).containsExactly(3);
        assertThat(json.read(hiddenRow + ".pendingFlagCount", List.class)).containsExactly(3);
        assertThat(json.read(hiddenRow + ".flagReasons.INAPPROPRIATE", List.class)).containsExactly(2);
        assertThat(json.read(hiddenRow + ".authorDisplayName", List.class)).containsExactly("김**");
        assertThat(json.read(hiddenRow + ".authorMemberCode", List.class)).containsExactly(commenter.getMemberCode());
        assertThat(json.read(hiddenRow + ".lastFlaggedAt", List.class)).hasSize(1);
        assertThat(clean).isNotNull();

        // 다시 공개하면 검토 대기에서 빠진다
        moderate(autoHidden, "VISIBLE", null).andExpect(status().isOk());
        assertThat(overviewFlaggedPending()).isEqualTo(before + 1);

        mockMvc.perform(get("/admin/comments").header("Authorization", bearer(commenter))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/comments").param("filter", "all").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());

        // 검토 완료·다시 공개는 작성자에게 알리지 않는다(자동 숨김 알림 1건만)
        awaitCount(1);
        Thread.sleep(300);
        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.data()).containsEntry("type", "COMMENT_MODERATED").containsEntry("commentId", autoHidden);
            assertThat(m.body()).contains("운영진 확인 전까지");
        });
    }

    // ---------- 작성자 조치 알림 ----------

    @Test
    void 관리자가_숨기면_작성자에게_사유와_이의_제기_안내를_보내고_댓글_내용은_싣지_않는다() throws Exception {
        Report report = report();
        Long id = comment(report, commenter, "전화번호 공개한 댓글");
        String token = device(commenter);

        moderate(id, "HIDDEN", "  개인정보 노출  ").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HIDDEN"));

        ExpoPushMessage m = await(token);
        assertThat(m.title()).isEqualTo("댓글이 운영 정책에 따라 숨겨졌어요");
        assertThat(m.body()).isEqualTo("'붕어빵 트럭' 제보에 남긴 댓글\n사유: 개인정보 노출\n"
                + "이의가 있으면 14일 안에 문의하기나 hongikonsupport@gmail.com 으로 알려 주세요");
        assertThat(m.toString()).doesNotContain("전화번호 공개한 댓글");
        assertThat(m.data()).containsEntry("type", "COMMENT_MODERATED").containsEntry("reportId", report.getId())
                .containsEntry("commentId", id).containsEntry("status", "HIDDEN");

        // 숨김 → 삭제도 알린다(사유 없으면 기본 문구). 같은 상태로 다시 숨김은 알리지 않는다.
        moderate(id, "DELETED", null).andExpect(status().isOk());
        awaitCount(2);
        assertThat(mine().get(1).title()).isEqualTo("댓글이 운영 정책에 따라 삭제됐어요");
        assertThat(mine().get(1).body()).contains("사유: 운영 정책 위반");

        Long again = comment(report, commenter, "두 번째 댓글");
        moderate(again, "HIDDEN", null).andExpect(status().isOk());
        awaitCount(3);
        moderate(again, "HIDDEN", null).andExpect(status().isOk());
        Thread.sleep(300);
        assertThat(mine()).hasSize(3);

        // 사유 200자 초과는 400
        moderate(again, "DELETED", "가".repeat(201)).andExpect(status().isBadRequest());
    }

    @Test
    void 신고_누적_자동_숨김은_작성자에게_확인_전_숨김을_알리고_관리자가_숨김을_확정하면_사유와_함께_다시_알린다() throws Exception {
        Report report = report();
        Long id = comment(report, commenter, "애매한 댓글");
        String token = device(commenter);
        for (int i = 0; i < 3; i++) {
            flag(user("신고" + i), report, id, "SPAM").andExpect(status().isCreated());
        }
        ExpoPushMessage auto = await(token);
        assertThat(auto.title()).isEqualTo("댓글이 운영 정책에 따라 숨겨졌어요");
        assertThat(auto.body()).contains("신고가 여러 건 접수돼 운영진 확인 전까지 숨겨졌어요").contains("14일 안에");

        moderate(id, "HIDDEN", "광고성 도배").andExpect(status().isOk());
        awaitCount(2);
        assertThat(mine().get(1).body()).contains("사유: 광고성 도배");
    }

    @Test
    void 결과_알림을_끈_작성자와_스스로_지운_댓글에는_알리지_않는다() throws Exception {
        Report report = report();
        User quiet = user("조용한");
        String quietToken = device(quiet);
        UserNotificationSetting off = new UserNotificationSetting(quiet.getId());
        off.changeReportStatusEnabled(false);
        settingRepository.save(off);
        Long quietComment = comment(report, quiet, "조용한 댓글");
        moderate(quietComment, "HIDDEN", "테스트").andExpect(status().isOk());

        Long selfDeleted = comment(report, commenter, "내가 지울 댓글");
        String token = device(commenter);
        mockMvc.perform(delete(comments(report) + "/" + selfDeleted).header("Authorization", bearer(commenter)))
                .andExpect(status().isNoContent());
        moderate(selfDeleted, "DELETED", null).andExpect(status().isOk());

        Thread.sleep(500);
        assertThat(mine()).noneMatch(m -> m.to().equals(quietToken) || m.to().equals(token));
    }

    // ---------- 픽스처 ----------

    private long overviewFlaggedPending() throws Exception {
        String body = mockMvc.perform(get("/admin/overview").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.comments.flaggedPending", Long.class);
    }

    private ExpoPushMessage await(String token) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            synchronized (sent) {
                for (ExpoPushMessage m : sent) {
                    if (m.to().equals(token)) {
                        return m;
                    }
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("작성자 알림이 오지 않았다");
    }

    private void awaitCount(int n) throws InterruptedException {
        for (int i = 0; i < 100 && mine().size() < n; i++) {
            Thread.sleep(50);
        }
        assertThat(mine()).hasSize(n);
    }

    private List<ExpoPushMessage> mine() {
        synchronized (sent) {
            return sent.stream().filter(m -> m.to().contains("[" + run + "-")).toList();
        }
    }

    private String device(User owner) {
        String token = "ExponentPushToken[" + run + "-" + UUID.randomUUID() + "]";
        userDeviceRepository.save(UserDevice.builder().user(owner).pushToken(token).tokenType(TokenType.EXPO).build());
        return token;
    }

    private User user(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private Report report() {
        LocalDateTime now = LocalDateTime.now();
        return reportRepository.save(Report.builder()
                .user(reportAuthor).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .status(ReportStatus.ACTIVE).build());
    }

    /** 1분 5개 작성 제한을 피하려고 저장소로 바로 만든다. */
    private Long comment(Report report, User author, String content) {
        return commentRepository.save(new ReportComment(report, author, content)).getId();
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private static String comments(Report report) {
        return "/reports/" + report.getId() + "/comments";
    }

    private ResultActions flag(User user, Report report, Long commentId, String reason) throws Exception {
        return mockMvc.perform(post(comments(report) + "/" + commentId + "/flags").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}"));
    }

    private ResultActions moderate(Long commentId, String status, String reason) throws Exception {
        String body = reason == null ? "{\"status\":\"" + status + "\"}"
                : "{\"status\":\"" + status + "\",\"reason\":\"" + reason + "\"}";
        return mockMvc.perform(patch("/admin/comments/" + commentId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
