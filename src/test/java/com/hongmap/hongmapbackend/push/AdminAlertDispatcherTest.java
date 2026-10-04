package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 알림 — 대상(관리자·본인 제외·끔), 내용(접두어·채널·data, 개인정보 없음), 묶음, 그리고 제보 등록·문의·신고 자동 숨김 →
 * 커밋 후 비동기 발송 연결. 묶음 간격은 1초로 줄여 돌린다. H2 인메모리 DB, Expo API는 ExpoPushClient를 모킹한다.
 * 다른 테스트의 관리자 기기와 섞이지 않게 이 테스트의 토큰(run 접두어)만 보고, 끝나면 기기를 비활성화한다.
 */
@SpringBootTest(properties = "push.admin-alert-window-seconds=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAlertDispatcherTest {

    @Autowired AdminAlertDispatcher dispatcher;
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired UserNotificationSettingRepository settingRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ExpoPushClient expoPushClient;

    String run;
    List<ExpoPushMessage> sent;

    @BeforeEach
    void setUp() {
        dispatcher.resetThrottle();
        run = UUID.randomUUID().toString().substring(0, 8);
        sent = Collections.synchronizedList(new ArrayList<>());
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            sent.addAll(batch);
            return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
        });
    }

    @AfterEach
    void deactivateDevices() {
        jdbcTemplate.update("UPDATE user_devices SET is_active = false WHERE push_token LIKE ?", "%[" + run + "-%");
    }

    // ---------- 대상·내용 ----------

    @Test
    void 관리자의_활성_Expo_기기에만_보내고_본인과_끈_관리자는_뺀다() {
        User admin = admin();
        String adminPhone = device(admin, TokenType.EXPO, true);
        device(admin, TokenType.EXPO, false);
        device(admin, TokenType.FCM, true);
        User normal = user();
        device(normal, TokenType.EXPO, true);
        User actorAdmin = admin();
        device(actorAdmin, TokenType.EXPO, true);
        User optedOut = admin();
        device(optedOut, TokenType.EXPO, true);
        UserNotificationSetting off = new UserNotificationSetting(optedOut.getId());
        off.changeAdminAlertsEnabled(false);
        settingRepository.save(off);

        dispatcher.offer(AdminAlertEvent.reportPending(41L, actorAdmin.getId(), "붕어빵 트럭", "홍문관", -1));

        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo(adminPhone);
            assertThat(m.title()).isEqualTo("[관리] 새 제보 승인 대기");
            assertThat(m.body()).isEqualTo("붕어빵 트럭 · 홍문관 B1층");
            assertThat(m.channelId()).isEqualTo("admin");
            assertThat(m.categoryId()).isEqualTo("admin");
            assertThat(m.data()).isEqualTo(Map.of("type", "ADMIN_REPORT_PENDING", "reportId", 41L, "count", 1));
        });
    }

    @Test
    void 문의_알림은_내용_없이_보내고_자동_숨김은_제보_제목을_담는다() {
        User admin = admin();
        device(admin, TokenType.EXPO, true);

        dispatcher.offer(AdminAlertEvent.feedback(7L, null));
        dispatcher.offer(AdminAlertEvent.reportFlagged(42L, 999L, "가짜 공지", null, null));

        assertThat(mine()).extracting(ExpoPushMessage::title, ExpoPushMessage::body).containsExactly(
                org.assertj.core.groups.Tuple.tuple("[관리] 새 문의", "새 문의가 도착했어요"),
                org.assertj.core.groups.Tuple.tuple("[관리] 신고 누적으로 자동 숨김", "가짜 공지 · 캠퍼스"));
        assertThat(mine().get(0).data()).isEqualTo(Map.of("type", "ADMIN_FEEDBACK", "feedbackId", 7L, "count", 1));
        assertThat(mine().get(1).data()).containsEntry("type", "ADMIN_REPORT_FLAGGED").containsEntry("reportId", 42L);
    }

    @Test
    void 몰려_온_건은_한_번에_묶어_N건으로_보낸다() throws Exception {
        User admin = admin();
        device(admin, TokenType.EXPO, true);

        dispatcher.offer(AdminAlertEvent.reportPending(1L, 900L, "첫 제보", "홍문관", 1));
        dispatcher.offer(AdminAlertEvent.reportPending(2L, 901L, "둘째 제보", "홍문관", 1));
        dispatcher.offer(AdminAlertEvent.reportPending(3L, 902L, "셋째 제보", "제2공학관", 3));
        assertThat(mine()).hasSize(1);

        awaitMine(m -> m.title().contains("2건"));
        assertThat(mine()).hasSize(2);
        assertThat(mine().get(1)).satisfies(m -> {
            assertThat(m.title()).isEqualTo("[관리] 새 제보 2건 승인 대기");
            assertThat(m.body()).isEqualTo("최근: 셋째 제보 · 제2공학관 3층");
            assertThat(m.data()).containsEntry("reportId", 3L).containsEntry("count", 2);
        });
    }

    // ---------- 커밋 후 비동기 연결 ----------

    @Test
    void 제보를_올리면_관리자에게_승인_대기_알림이_간다() throws Exception {
        User admin = admin();
        String adminPhone = device(admin, TokenType.EXPO, true);
        User author = user();
        Building building = building();
        String body = """
                {"buildingId": %d, "floor": 2, "lat": 37.55, "lng": 126.925, "category": "FOOD_TRUCK",
                 "title": "붕어빵 트럭", "startsAt": "2026-10-01T08:00:00.000Z", "endsAt": "%s"}
                """.formatted(building.getId(), Instant.now().plus(Duration.ofHours(2)).toString());

        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        awaitMine(m -> m.to().equals(adminPhone));
        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("[관리] 새 제보 승인 대기");
            assertThat(m.body()).isEqualTo("붕어빵 트럭 · " + building.getName() + " 2층");
            assertThat(m.data()).containsEntry("type", "ADMIN_REPORT_PENDING");
        });
    }

    @Test
    void 문의가_오면_내용과_연락처_없이_알린다() throws Exception {
        User admin = admin();
        device(admin, TokenType.EXPO, true);

        mockMvc.perform(post("/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"비밀번호 1234 유출\",\"contact\":\"me@example.com\"}"))
                .andExpect(status().isCreated());

        awaitMine(m -> true);
        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.body()).isEqualTo("새 문의가 도착했어요");
            assertThat(m.toString()).doesNotContain("1234").doesNotContain("me@example.com");
            assertThat(m.data()).containsEntry("type", "ADMIN_FEEDBACK").containsKey("feedbackId");
        });
    }

    @Test
    void 신고가_쌓여_자동_숨김되면_한_번만_알린다() throws Exception {
        User admin = admin();
        device(admin, TokenType.EXPO, true);
        Report report = activeReport(user());

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(user()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                    .andExpect(status().isCreated());
        }

        awaitMine(m -> true);
        Thread.sleep(1500); // 묶음 간격(1초)이 지나도 더 오지 않는지
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.HIDDEN);
        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("[관리] 신고 누적으로 자동 숨김");
            assertThat(m.data()).containsEntry("type", "ADMIN_REPORT_FLAGGED").containsEntry("reportId", report.getId());
        });
    }

    @Test
    void 승인된_제보에_새_신고가_쌓이면_숨기고_관리자에게_한_번_알린다() throws Exception {
        User admin = admin();
        device(admin, TokenType.EXPO, true);
        Report report = activeReport(user());
        // 운영진 승인(reviewedAt) — 운영의 모든 공개 제보는 이 상태다. 승인 전 신고 1건은 이미 본 것이라 세지 않는다.
        mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE reports SET reviewed_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now()), report.getId());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(user()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                    .andExpect(status().isCreated());
        }
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.ACTIVE);

        mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(user()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isCreated());

        awaitMine(m -> true);
        Thread.sleep(1500); // 묶음 간격(1초)이 지나도 더 오지 않는지
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.HIDDEN);
        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("[관리] 신고 누적으로 자동 숨김");
            assertThat(m.data()).containsEntry("type", "ADMIN_REPORT_FLAGGED").containsEntry("reportId", report.getId());
        });
    }

    @Test
    void 설정_API로_관리자_알림을_끄고_켤_수_있다() throws Exception {
        User admin = admin();
        device(admin, TokenType.EXPO, true);
        String base = "/users/me/notification-settings";

        mockMvc.perform(get(base).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.adminAlerts").value(true));
        mockMvc.perform(patch(base).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"adminAlerts\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminAlerts").value(false))
                .andExpect(jsonPath("$.reportStatus").value(true));

        dispatcher.offer(AdminAlertEvent.feedback(8L, null));
        assertThat(mine()).isEmpty();

        mockMvc.perform(patch(base).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"adminAlerts\":true}"))
                .andExpect(jsonPath("$.adminAlerts").value(true));
        dispatcher.resetThrottle();
        dispatcher.offer(AdminAlertEvent.feedback(9L, null));
        assertThat(mine()).hasSize(1);
    }

    // ---------- 픽스처 ----------

    private List<ExpoPushMessage> mine() {
        synchronized (sent) {
            return sent.stream().filter(m -> m.to().contains("[" + run + "-")).toList();
        }
    }

    private void awaitMine(Predicate<ExpoPushMessage> condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (mine().stream().anyMatch(condition)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("관리자 알림이 오지 않음: " + mine());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private User user() {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
    }

    private User admin() {
        User admin = user();
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        return admin;
    }

    private String device(User owner, TokenType tokenType, boolean active) {
        String token = "ExponentPushToken[" + run + "-" + UUID.randomUUID() + "]";
        UserDevice device = UserDevice.builder().user(owner).pushToken(token).tokenType(tokenType).build();
        if (!active) {
            device.deactivate();
        }
        userDeviceRepository.save(device);
        return token;
    }

    private Building building() {
        return buildingRepository.save(Building.builder()
                .name("테스트관-" + run)
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    private Report activeReport(User author) {
        LocalDateTime now = LocalDateTime.now();
        return reportRepository.save(Report.builder()
                .user(author).building(building()).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭").status(ReportStatus.ACTIVE)
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .build());
    }
}
