package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.comment.ReportCommentCreatedEvent;
import com.hongmap.hongmapbackend.comment.ReportCommentPushDispatcher;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.push.ExpoPushClient;
import com.hongmap.hongmapbackend.push.ExpoPushMessage;
import com.hongmap.hongmapbackend.push.ExpoPushTicket;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.user.DevicePlatform;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제보 커뮤니티 — 🔥(HOT·이정표), 관심 제보(시작·곧 끝남·새 댓글 알림, 자동 정리), 작성자 알림 끄기, 조회 수, 댓글 👍,
 * 삭제 연쇄. 푸시는 Expo 클라이언트를 모킹해 메시지를 모은다(디스패처·스케줄러는 동기로 부른다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportCommunityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ReportCommunityPushDispatcher communityDispatcher;
    @Autowired ReportCommentPushDispatcher commentDispatcher;
    @Autowired ReportFollowScheduler followScheduler;
    @Autowired CommunityActionLimiter limiter;
    @Autowired UserNotificationSettingRepository settingRepository;

    @MockitoBean ExpoPushClient expoPushClient;

    List<ExpoPushMessage> sent;
    User author;
    User fan;
    Building building;

    @BeforeEach
    void setUp() {
        sent = Collections.synchronizedList(new ArrayList<>());
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            sent.addAll(batch);
            return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
        });
        communityDispatcher.resetForTest();
        limiter.resetForTest();
        author = user("홍길동");
        fan = user("김철수");
        building = buildingRepository.save(Building.builder()
                .name("테스트관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    // ---------- 🔥 ----------

    @Test
    void 불은_로그인_남의_제보에만_한_번_끄면_빠지고_목록에_수와_내_상태가_붙는다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        mockMvc.perform(put(fire(report))).andExpect(status().isUnauthorized());
        mockMvc.perform(put(fire(report)).header("Authorization", bearer(author))).andExpect(status().isBadRequest());

        mockMvc.perform(put(fire(report)).header("Authorization", bearer(fan))).andExpect(status().isOk())
                .andExpect(jsonPath("$.fired").value(true))
                .andExpect(jsonPath("$.fireCount").value(1))
                .andExpect(jsonPath("$.recentFireCount").value(1))
                .andExpect(jsonPath("$.hot").value(false));
        // 다시 눌러도 그대로 1
        mockMvc.perform(put(fire(report)).header("Authorization", bearer(fan)))
                .andExpect(jsonPath("$.fireCount").value(1));

        mockMvc.perform(get("/reports").header("Authorization", bearer(fan)))
                .andExpect(jsonPath(item(report) + ".fireCount").value(1))
                .andExpect(jsonPath(item(report) + ".firedByMe").value(true))
                .andExpect(jsonPath(item(report) + ".followedByMe").value(false))
                .andExpect(jsonPath(item(report) + ".viewCount").value(0))
                .andExpect(jsonPath(item(report) + ".notifyEnabled").value(contains(nullValue())));
        mockMvc.perform(get("/reports"))
                .andExpect(jsonPath(item(report) + ".fireCount").value(1))
                .andExpect(jsonPath(item(report) + ".firedByMe").value(false))
                // 누가 눌렀는지는 어디에도 싣지 않는다
                .andExpect(content().string(not(containsString("김철수"))));

        mockMvc.perform(delete(fire(report)).header("Authorization", bearer(fan))).andExpect(status().isOk())
                .andExpect(jsonPath("$.fired").value(false))
                .andExpect(jsonPath("$.fireCount").value(0));
    }

    @Test
    void 공개되지_않은_제보는_404_끝난_제보는_409() throws Exception {
        for (ReportStatus status : List.of(ReportStatus.PENDING, ReportStatus.HIDDEN, ReportStatus.REJECTED)) {
            Report report = report(status);
            mockMvc.perform(put(fire(report)).header("Authorization", bearer(fan))).andExpect(status().isNotFound());
            mockMvc.perform(put(follow(report)).header("Authorization", bearer(fan))).andExpect(status().isNotFound());
            mockMvc.perform(post(views(report))).andExpect(status().isNotFound());
        }
        Report ended = reportRepository.save(baseReport(ReportStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusHours(3)).endsAt(LocalDateTime.now().minusMinutes(1)).build());
        mockMvc.perform(put(fire(ended)).header("Authorization", bearer(fan))).andExpect(status().isConflict());
        mockMvc.perform(put(follow(ended)).header("Authorization", bearer(fan))).andExpect(status().isConflict());
    }

    @Test
    void 최근_불_5개면_HOT_sort_hot_은_불_있는_제보만_최근_불_순() throws Exception {
        Report hot = report(ReportStatus.ACTIVE);
        Report warm = report(ReportStatus.ACTIVE);
        Report cold = report(ReportStatus.ACTIVE);
        for (int i = 0; i < 5; i++) {
            User u = user("팬" + i);
            ResultActions r = mockMvc.perform(put(fire(hot)).header("Authorization", bearer(u)));
            if (i == 4) {
                r.andExpect(jsonPath("$.hot").value(true)).andExpect(jsonPath("$.recentFireCount").value(5));
            }
        }
        mockMvc.perform(put(fire(warm)).header("Authorization", bearer(fan)));
        // warm 의 오래된 🔥 2개(창 밖)는 전체 수에만 든다
        for (int i = 0; i < 2; i++) {
            User u = user("옛팬" + i);
            jdbcTemplate.update("INSERT INTO report_reactions (report_id, user_id, created_at) VALUES (?, ?, ?)",
                    warm.getId(), u.getId(), LocalDateTime.now().minusHours(2));
        }

        mockMvc.perform(get("/reports"))
                .andExpect(jsonPath(item(hot) + ".hot").value(true))
                .andExpect(jsonPath(item(warm) + ".hot").value(false))
                .andExpect(jsonPath(item(warm) + ".fireCount").value(3))
                .andExpect(jsonPath(item(warm) + ".recentFireCount").value(1))
                .andExpect(jsonPath(item(cold) + ".fireCount").value(0));

        String body = mockMvc.perform(get("/reports").param("sort", "hot")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.reports[*].id");
        assertThat(ids).doesNotContain(cold.getId().intValue());
        assertThat(ids.indexOf(hot.getId().intValue())).isLessThan(ids.indexOf(warm.getId().intValue()));
    }

    @Test
    void 불_관심_좋아요_누르기는_1분_20번까지() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        for (int i = 0; i < 20; i++) {
            mockMvc.perform((i % 2 == 0 ? put(fire(report)) : delete(fire(report))).header("Authorization", bearer(fan)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(put(follow(report)).header("Authorization", bearer(fan))).andExpect(status().isTooManyRequests());
    }

    @Test
    void 불_10개를_처음_넘으면_작성자에게_한_번_알리고_작성자가_알림을_끄면_안_보낸다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(put(fire(report)).header("Authorization", bearer(user("팬" + i)))).andExpect(status().isOk());
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fire_milestone_sent FROM report_engagement WHERE report_id = ?", Integer.class, report.getId()))
                .isEqualTo(10);

        // 끄고 다시 눌러 10 을 다시 넘어도 이정표는 그대로(한 번만)
        User tenth = user("열번째");
        mockMvc.perform(put(fire(report)).header("Authorization", bearer(tenth)));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fire_milestone_sent FROM report_engagement WHERE report_id = ?", Integer.class, report.getId()))
                .isEqualTo(10);

        device(author);
        sent.clear();
        ReportFireMilestoneEvent event = new ReportFireMilestoneEvent(report.getId(), author.getId(), "붕어빵 트럭", 10);
        assertThat(communityDispatcher.sendFireMilestone(event)).isEqualTo(1);
        assertThat(sent.get(0).title()).isEqualTo("내 제보에 🔥가 10개 모였어요");
        assertThat(sent.get(0).data()).containsEntry("type", "REPORT_FIRE").containsEntry("milestone", 10);

        mockMvc.perform(put(notifications(report)).header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        assertThat(communityDispatcher.sendFireMilestone(event)).isZero();
    }

    // ---------- 작성자 알림 ----------

    @Test
    void 작성자만_이_제보_알림을_끄고_켜고_끄면_댓글_알림도_안_간다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        mockMvc.perform(put(notifications(report)).header("Authorization", bearer(fan))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/reports").header("Authorization", bearer(author)))
                .andExpect(jsonPath(item(report) + ".notifyEnabled").value(true));

        mockMvc.perform(put(notifications(report)).header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(get("/reports").header("Authorization", bearer(author)))
                .andExpect(jsonPath(item(report) + ".notifyEnabled").value(false));

        device(author);
        sent.clear();
        commentDispatcher.dispatch(new ReportCommentCreatedEvent(report.getId(), author.getId(), fan.getId(),
                "붕어빵 트럭", "다 팔렸어요"));
        assertThat(sent).isEmpty();

        mockMvc.perform(put(notifications(report)).header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isOk());
        commentDispatcher.dispatch(new ReportCommentCreatedEvent(report.getId(), author.getId(), fan.getId(),
                "붕어빵 트럭", "다 팔렸어요"));
        assertThat(sent).hasSize(1);
    }

    // ---------- 관심 ----------

    @Test
    void 관심은_남의_제보만_목록에_내_상태가_붙고_해제된다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        mockMvc.perform(put(follow(report))).andExpect(status().isUnauthorized());
        mockMvc.perform(put(follow(report)).header("Authorization", bearer(author))).andExpect(status().isBadRequest());
        mockMvc.perform(put(follow(report)).header("Authorization", bearer(fan))).andExpect(status().isOk())
                .andExpect(jsonPath("$.followed").value(true));
        mockMvc.perform(get("/reports").header("Authorization", bearer(fan)))
                .andExpect(jsonPath(item(report) + ".followedByMe").value(true));
        mockMvc.perform(delete(follow(report)).header("Authorization", bearer(fan))).andExpect(status().isOk())
                .andExpect(jsonPath("$.followed").value(false));
        mockMvc.perform(get("/reports").header("Authorization", bearer(fan)))
                .andExpect(jsonPath(item(report) + ".followedByMe").value(false));
    }

    @Test
    void 관심_제보가_시작하면_곧_끝나면_한_번씩_알리고_끝나면_관심이_풀린다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        // 2시간 뒤 시작하는 예정 제보(ACTIVE)
        Report report = reportRepository.save(baseReport(ReportStatus.ACTIVE)
                .startsAt(now.plusHours(2)).endsAt(now.plusHours(4)).build());
        mockMvc.perform(put(follow(report)).header("Authorization", bearer(fan))).andExpect(status().isOk());
        device(fan);

        assertThat(followScheduler.run(now)).isZero();

        // 시작 1분 뒤: 시작 알림 한 번
        assertThat(followScheduler.run(now.plusHours(2).plusMinutes(1))).isEqualTo(1);
        assertThat(sent.get(0).title()).isEqualTo("관심 제보가 시작됐어요");
        assertThat(sent.get(0).data()).containsEntry("type", "REPORT_FOLLOW").containsEntry("kind", "START");
        assertThat(followScheduler.run(now.plusHours(2).plusMinutes(2))).isZero();

        // 끝나기 20분 전: 곧 끝나요 한 번
        sent.clear();
        assertThat(followScheduler.run(now.plusHours(4).minusMinutes(20))).isEqualTo(1);
        assertThat(sent.get(0).title()).isEqualTo("관심 제보가 곧 끝나요");
        assertThat(sent.get(0).body()).contains("30분 뒤에 끝나요");
        assertThat(followScheduler.run(now.plusHours(4).minusMinutes(10))).isZero();

        // 끝난 뒤: 관심이 지워진다
        followScheduler.run(now.plusHours(4).plusMinutes(1));
        assertThat(count("report_follows", report)).isZero();
    }

    @Test
    void 숨겨진_제보의_관심은_정리되고_시작_후_등록한_관심에는_시작_알림이_없다() throws Exception {
        Report live = report(ReportStatus.ACTIVE);
        mockMvc.perform(put(follow(live)).header("Authorization", bearer(fan))).andExpect(status().isOk());
        device(fan);
        assertThat(followScheduler.run(LocalDateTime.now())).isZero();

        jdbcTemplate.update("UPDATE reports SET status = 'HIDDEN' WHERE id = ?", live.getId());
        followScheduler.run(LocalDateTime.now());
        assertThat(count("report_follows", live)).isZero();
    }

    @Test
    void 관심_제보의_새_댓글은_관심_등록자에게_묶어서_알리고_댓글쓴이_작성자는_뺀다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        User other = user("이영희");
        for (User u : List.of(fan, other)) {
            mockMvc.perform(put(follow(report)).header("Authorization", bearer(u))).andExpect(status().isOk());
            device(u);
        }
        device(author);
        ReportCommentCreatedEvent byFan = new ReportCommentCreatedEvent(report.getId(), author.getId(), fan.getId(),
                "붕어빵 트럭", "지금 줄 10명");
        assertThat(communityDispatcher.dispatchFollowComment(byFan)).isEqualTo(1);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).title()).isEqualTo("관심 제보에 새 댓글이 달렸어요");
        assertThat(sent.get(0).data()).containsEntry("kind", "COMMENT");
        // 30분 안의 다음 댓글은 묶는다
        assertThat(communityDispatcher.dispatchFollowComment(byFan)).isZero();

        // "내 제보 결과 알림"을 끈 사람에게는 보내지 않는다
        communityDispatcher.resetForTest();
        UserNotificationSetting setting = new UserNotificationSetting(other.getId());
        setting.changeReportStatusEnabled(false);
        settingRepository.save(setting);
        assertThat(communityDispatcher.dispatchFollowComment(byFan)).isZero();
    }

    // ---------- 조회 수 ----------

    @Test
    void 조회는_계정이나_설치_id_로_하루_한_번_식별값_원문은_저장하지_않는다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        mockMvc.perform(post(views(report))).andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(false)).andExpect(jsonPath("$.viewCount").value(0));
        mockMvc.perform(post(views(report)).header("X-Install-Id", "bad id!")).andExpect(jsonPath("$.counted").value(false));

        String installId = "install-" + UUID.randomUUID();
        mockMvc.perform(post(views(report)).header("X-Install-Id", installId))
                .andExpect(jsonPath("$.counted").value(true)).andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(post(views(report)).header("X-Install-Id", installId))
                .andExpect(jsonPath("$.counted").value(false)).andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(post(views(report)).header("Authorization", bearer(fan)))
                .andExpect(jsonPath("$.counted").value(true)).andExpect(jsonPath("$.viewCount").value(2));
        mockMvc.perform(post(views(report)).header("Authorization", bearer(fan)).header("X-Install-Id", installId))
                .andExpect(jsonPath("$.counted").value(false));

        mockMvc.perform(get("/reports")).andExpect(jsonPath(item(report) + ".viewCount").value(2));
        List<String> keys = jdbcTemplate.queryForList(
                "SELECT viewer_key FROM report_view_marks WHERE report_id = ?", String.class, report.getId());
        assertThat(keys).hasSize(2).allSatisfy(k -> assertThat(k).matches("[0-9a-f]{32}"));

        // 2일 지난 표식은 스케줄러가 지운다(조회 수는 남는다)
        jdbcTemplate.update("UPDATE report_view_marks SET view_date = ? WHERE report_id = ?",
                java.time.LocalDate.now().minusDays(3), report.getId());
        followScheduler.run(LocalDateTime.now());
        assertThat(count("report_view_marks", report)).isZero();
        mockMvc.perform(get("/reports")).andExpect(jsonPath(item(report) + ".viewCount").value(2));
    }

    // ---------- 댓글 👍 ----------

    @Test
    void 댓글_좋아요는_남의_댓글에_한_번_수와_내_상태_인기순() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long first = commentId(writeComment(author, report, "첫 댓글"));
        Long second = commentId(writeComment(fan, report, "둘째 댓글"));
        Long reply = commentId(mockMvc.perform(post(comments(report)).header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"답글\",\"parentId\":" + second + "}")));

        mockMvc.perform(put(like(report, second))).andExpect(status().isUnauthorized());
        mockMvc.perform(put(like(report, second)).header("Authorization", bearer(fan))).andExpect(status().isBadRequest());
        mockMvc.perform(put(like(report, second)).header("Authorization", bearer(author))).andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(1));
        mockMvc.perform(put(like(report, second)).header("Authorization", bearer(user("박민수")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(2));
        mockMvc.perform(put(like(report, reply)).header("Authorization", bearer(fan))).andExpect(status().isOk());

        // 기본(오래된 순): first, second. 인기순: second 먼저
        mockMvc.perform(get(comments(report)).header("Authorization", bearer(author)))
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.content[0].likeCount").value(0))
                .andExpect(jsonPath("$.content[1].likeCount").value(2))
                .andExpect(jsonPath("$.content[1].likedByMe").value(true))
                .andExpect(jsonPath("$.content[1].replies[0].likeCount").value(1))
                .andExpect(jsonPath("$.content[1].replies[0].likedByMe").value(false));
        mockMvc.perform(get(comments(report)).param("order", "popular"))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[0].likedByMe").value(false))
                .andExpect(jsonPath("$.content[1].id").value(first));
        mockMvc.perform(get(comments(report) + "/" + second + "/replies").header("Authorization", bearer(fan)))
                .andExpect(jsonPath("$.content[0].likeCount").value(1))
                .andExpect(jsonPath("$.content[0].likedByMe").value(true));

        mockMvc.perform(delete(like(report, second)).header("Authorization", bearer(author))).andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.likeCount").value(1));
    }

    // ---------- 삭제 연쇄 ----------

    @Test
    void 제보를_지우면_불_관심_조회_좋아요가_함께_지워지고_탈퇴하면_내_기록이_지워진다() throws Exception {
        Report report = report(ReportStatus.ACTIVE);
        Long commentId = commentId(writeComment(author, report, "댓글"));
        mockMvc.perform(put(fire(report)).header("Authorization", bearer(fan))).andExpect(status().isOk());
        mockMvc.perform(put(follow(report)).header("Authorization", bearer(fan))).andExpect(status().isOk());
        mockMvc.perform(put(like(report, commentId)).header("Authorization", bearer(fan))).andExpect(status().isOk());
        mockMvc.perform(post(views(report)).header("Authorization", bearer(fan))).andExpect(status().isOk());

        // 팬 탈퇴 → 팬의 🔥·관심·👍 삭제, 조회 수는 익명 집계라 남는다
        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(fan))).andExpect(status().isNoContent());
        assertThat(count("report_reactions", report)).isZero();
        assertThat(count("report_follows", report)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report_comment_likes WHERE comment_id = ?",
                Long.class, commentId)).isZero();

        User another = user("이영희");
        mockMvc.perform(put(fire(report)).header("Authorization", bearer(another))).andExpect(status().isOk());
        mockMvc.perform(delete("/reports/" + report.getId()).header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());
        for (String table : List.of("report_reactions", "report_follows", "report_view_marks", "report_engagement")) {
            assertThat(count(table, report)).as(table).isZero();
        }
    }

    // ---------- 도우미 ----------

    private long count(String table, Report report) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE report_id = ?", Long.class,
                report.getId());
    }

    private User user(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private void device(User user) {
        userDeviceRepository.save(UserDevice.builder()
                .user(user).pushToken("ExponentPushToken[" + UUID.randomUUID() + "]")
                .tokenType(TokenType.EXPO).platform(DevicePlatform.IOS).build());
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

    private static String item(Report report) {
        return "$.reports[?(@.id == " + report.getId() + ")]";
    }

    private static String fire(Report report) {
        return "/reports/" + report.getId() + "/fire";
    }

    private static String follow(Report report) {
        return "/reports/" + report.getId() + "/follow";
    }

    private static String notifications(Report report) {
        return "/reports/" + report.getId() + "/notifications";
    }

    private static String views(Report report) {
        return "/reports/" + report.getId() + "/views";
    }

    private static String comments(Report report) {
        return "/reports/" + report.getId() + "/comments";
    }

    private static String like(Report report, Long commentId) {
        return comments(report) + "/" + commentId + "/like";
    }

    private ResultActions writeComment(User user, Report report, String content) throws Exception {
        return mockMvc.perform(post(comments(report)).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + content + "\"}"));
    }

    private static Long commentId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.id", Long.class);
    }
}
