package com.hongmap.hongmapbackend.push;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 승인 대기 리마인드 — 30분·2시간 두 번까지, 회차당 한 번 묶음, KST 방해 금지(00–08시) 미룸, 끄기·비 PENDING 제외,
 * 서버 여러 대 동시 실행 시 한 번만. 시각은 고정 Clock 으로 돌린다(서버 시각은 UTC).
 * 다른 테스트 데이터와 섞이지 않게 제보 created_at 을 과거(2020년)로 옮겨 쓰고, 이 테스트의 토큰(run 접두어)만 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminReportReminderTest {

    /** 2020-03-02 12:00 KST — 방해 금지 밖 */
    static final LocalDateTime BASE = LocalDateTime.of(2020, 3, 2, 3, 0);

    @Autowired ReportRepository reportRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserNotificationSettingRepository settingRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ExpoPushSender expoPushSender;
    @Autowired PushProperties pushProperties;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ExpoPushClient expoPushClient;

    String run;
    List<ExpoPushMessage> sent;
    List<Long> myReports;

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        sent = Collections.synchronizedList(new ArrayList<>());
        myReports = new ArrayList<>();
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            sent.addAll(batch);
            return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
        });
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("UPDATE user_devices SET is_active = false WHERE push_token LIKE ?", "%[" + run + "-%");
        for (Long id : myReports) {
            jdbcTemplate.update("UPDATE reports SET status = 'DELETED' WHERE id = ?", id);
        }
    }

    @Test
    void 삼십분_전에는_안_보내고_삼십분에_한_번_두시간에_두번째_그_뒤로는_없다() {
        String phone = device(admin());
        Report report = report(ReportStatus.PENDING, BASE);

        assertThat(runAt(BASE.plusMinutes(29))).isZero();
        assertThat(mine()).isEmpty();
        assertThat(reminderCount(report)).isZero();

        runAt(BASE.plusMinutes(30));
        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo(phone);
            assertThat(m.title()).isEqualTo("[관리] 검토 대기 중인 제보가 1건 있어요");
            assertThat(m.body()).isEqualTo("가장 오래된 것 30분 전");
            assertThat(m.channelId()).isEqualTo("admin");
            assertThat(m.categoryId()).isEqualTo("admin");
            assertThat(m.data()).isEqualTo(Map.of("type", "ADMIN_REPORT_REMINDER", "count", 1L, "oldestReportId", report.getId()));
        });
        assertThat(reminderCount(report)).isEqualTo(1);

        runAt(BASE.plusMinutes(40));
        runAt(BASE.plusMinutes(110));
        assertThat(mine()).hasSize(1);

        runAt(BASE.plusMinutes(120));
        assertThat(mine()).hasSize(2);
        assertThat(mine().get(1).body()).isEqualTo("가장 오래된 것 2시간 전");
        assertThat(reminderCount(report)).isEqualTo(2);

        runAt(BASE.plusMinutes(130));
        runAt(BASE.plusHours(5));
        runAt(BASE.plusDays(1));
        assertThat(mine()).hasSize(2);
    }

    @Test
    void 여러_건이_한_회차에_걸리면_한_번에_묶고_가장_오래된_것을_가리킨다() {
        device(admin());
        Report older = report(ReportStatus.PENDING, BASE);
        report(ReportStatus.PENDING, BASE.plusMinutes(5));
        report(ReportStatus.PENDING, BASE.plusMinutes(50)); // 아직 30분 안 됨 — 개수에 안 들어감

        runAt(BASE.plusMinutes(60));

        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("[관리] 검토 대기 중인 제보가 2건 있어요");
            assertThat(m.body()).isEqualTo("가장 오래된 것 1시간 전");
            assertThat(m.data()).containsEntry("count", 2L).containsEntry("oldestReportId", older.getId());
        });
    }

    @Test
    void 방해_금지_시간에는_미루고_8시에_한_번_요약한다() {
        device(admin());
        // 2020-03-02 23:00 KST(14:00 UTC) 등록
        LocalDateTime created = LocalDateTime.of(2020, 3, 2, 14, 0);
        Report report = report(ReportStatus.PENDING, created);

        runAt(created.plusMinutes(50)); // 23:50 KST — 첫 리마인드
        assertThat(mine()).hasSize(1);

        runAt(LocalDateTime.of(2020, 3, 2, 15, 0)); // 00:00 KST
        runAt(LocalDateTime.of(2020, 3, 2, 17, 0)); // 02:00 KST — 2시간 넘었지만 방해 금지
        runAt(LocalDateTime.of(2020, 3, 2, 22, 50)); // 07:50 KST
        assertThat(mine()).hasSize(1);
        assertThat(reminderCount(report)).isEqualTo(1);

        runAt(LocalDateTime.of(2020, 3, 2, 23, 0)); // 08:00 KST — 요약
        assertThat(mine()).hasSize(2);
        assertThat(mine().get(1).title()).isEqualTo("[관리] 검토 대기 중인 제보가 1건 있어요");
        assertThat(mine().get(1).body()).isEqualTo("가장 오래된 것 9시간 전");
        assertThat(reminderCount(report)).isEqualTo(2);

        runAt(LocalDateTime.of(2020, 3, 2, 23, 10));
        assertThat(mine()).hasSize(2);
    }

    @Test
    void 밤에_들어와_밤새_쌓인_제보는_8시에_한_번만_오고_다음은_간격을_둔다() {
        device(admin());
        Report report = report(ReportStatus.PENDING, LocalDateTime.of(2020, 3, 2, 16, 0)); // 01:00 KST

        runAt(LocalDateTime.of(2020, 3, 2, 20, 0)); // 05:00 KST
        assertThat(mine()).isEmpty();

        runAt(LocalDateTime.of(2020, 3, 2, 23, 0)); // 08:00 KST
        runAt(LocalDateTime.of(2020, 3, 2, 23, 10));
        assertThat(mine()).hasSize(1);
        assertThat(reminderCount(report)).isEqualTo(1);

        runAt(LocalDateTime.of(2020, 3, 3, 0, 30)); // 09:30 KST — 90분 뒤 두 번째
        assertThat(mine()).hasSize(2);
    }

    @Test
    void 끈_관리자와_일반_유저는_받지_않는다() {
        String on = device(admin());
        User optedOut = admin();
        device(optedOut);
        UserNotificationSetting off = new UserNotificationSetting(optedOut.getId());
        off.changeAdminAlertsEnabled(false);
        settingRepository.save(off);
        device(user());
        report(ReportStatus.PENDING, BASE);

        runAt(BASE.plusMinutes(30));

        assertThat(mine()).extracting(ExpoPushMessage::to).containsExactly(on);
    }

    @Test
    void 받을_관리자_기기가_없으면_선점하지_않는다() {
        User optedOut = admin();
        device(optedOut);
        UserNotificationSetting off = new UserNotificationSetting(optedOut.getId());
        off.changeAdminAlertsEnabled(false);
        settingRepository.save(off);
        // 다른 테스트가 남긴 관리자 기기가 있으면 이 경우를 만들 수 없으므로 모두 끈다.
        jdbcTemplate.update("""
                UPDATE user_devices SET is_active = false
                WHERE user_id IN (SELECT id FROM users WHERE role = 'ADMIN')""");
        Report report = report(ReportStatus.PENDING, BASE);

        assertThat(runAt(BASE.plusMinutes(30))).isZero();
        assertThat(sent).isEmpty();
        assertThat(reminderCount(report)).isZero();
    }

    @Test
    void 이미_끝난_승인_대기_제보는_리마인드하지_않고_개수에서도_뺀다() {
        device(admin());
        // 30분 안에 끝나 버린 제보 — 승인해도 지도에 뜨지 않는다
        Report expired = report(ReportStatus.PENDING, BASE, BASE.plusMinutes(20));
        Report live = report(ReportStatus.PENDING, BASE.plusMinutes(5), BASE.plusDays(1));

        runAt(BASE.plusMinutes(60));

        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("[관리] 검토 대기 중인 제보가 1건 있어요");
            assertThat(m.body()).isEqualTo("가장 오래된 것 55분 전");
            assertThat(m.data()).containsEntry("count", 1L).containsEntry("oldestReportId", live.getId());
        });
        assertThat(reminderCount(expired)).isZero();
        assertThat(reminderCount(live)).isEqualTo(1);
    }

    @Test
    void 승인_대기가_아닌_제보는_빠진다() {
        device(admin());
        Report active = report(ReportStatus.ACTIVE, BASE);
        Report rejected = report(ReportStatus.REJECTED, BASE);

        runAt(BASE.plusMinutes(30));
        runAt(BASE.plusHours(3));

        assertThat(mine()).isEmpty();
        assertThat(reminderCount(active)).isZero();
        assertThat(reminderCount(rejected)).isZero();
    }

    @Test
    void 서버_두_대가_같은_시각에_돌아도_한_번만_보낸다() {
        device(admin());
        report(ReportStatus.PENDING, BASE);
        Clock clock = clockAt(BASE.plusMinutes(30));

        int first = reminder(clock).remind();
        int second = reminder(clock).remind();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(mine()).hasSize(1);
    }

    @Test
    void 방해_금지_구간_계산() {
        AdminReportReminder r = reminder(Clock.systemUTC());
        assertThat(r.isQuietHours(LocalDateTime.of(2020, 3, 2, 14, 59))).isFalse(); // 23:59 KST
        assertThat(r.isQuietHours(LocalDateTime.of(2020, 3, 2, 15, 0))).isTrue();   // 00:00
        assertThat(r.isQuietHours(LocalDateTime.of(2020, 3, 2, 22, 59))).isTrue();  // 07:59
        assertThat(r.isQuietHours(LocalDateTime.of(2020, 3, 2, 23, 0))).isFalse();  // 08:00
        r.quietStartHour = 22;
        r.quietEndHour = 7;
        assertThat(r.isQuietHours(LocalDateTime.of(2020, 3, 2, 13, 0))).isTrue();   // 22:00
        assertThat(r.isQuietHours(LocalDateTime.of(2020, 3, 2, 22, 0))).isFalse();  // 07:00
        assertThat(AdminReportReminder.ago(45)).isEqualTo("45분 전");
        assertThat(AdminReportReminder.ago(125)).isEqualTo("2시간 5분 전");
    }

    // ---------- 픽스처 ----------

    private int runAt(LocalDateTime nowUtc) {
        return reminder(clockAt(nowUtc)).remind();
    }

    private Clock clockAt(LocalDateTime nowUtc) {
        return Clock.fixed(nowUtc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    private AdminReportReminder reminder(Clock clock) {
        return new AdminReportReminder(reportRepository, userDeviceRepository, expoPushSender, pushProperties, clock);
    }

    private List<ExpoPushMessage> mine() {
        synchronized (sent) {
            return sent.stream().filter(m -> m.to().contains("[" + run + "-")).toList();
        }
    }

    private int reminderCount(Report report) {
        Integer n = jdbcTemplate.queryForObject("SELECT admin_reminder_count FROM reports WHERE id = ?", Integer.class,
                report.getId());
        return n == null ? 0 : n;
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

    private String device(User owner) {
        String token = "ExponentPushToken[" + run + "-" + UUID.randomUUID() + "]";
        userDeviceRepository.save(UserDevice.builder().user(owner).pushToken(token).tokenType(TokenType.EXPO).build());
        return token;
    }

    private Report report(ReportStatus status, LocalDateTime createdAt) {
        return report(status, createdAt, createdAt.plus(Duration.ofDays(1)));
    }

    private Report report(ReportStatus status, LocalDateTime createdAt, LocalDateTime endsAt) {
        Building building = buildingRepository.save(Building.builder()
                .name("리마인드관-" + run + "-" + UUID.randomUUID().toString().substring(0, 4))
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        Report report = reportRepository.save(Report.builder()
                .user(user()).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭").status(status)
                .startsAt(createdAt).endsAt(endsAt)
                .build());
        // Hibernate 와 같은 방식(Timestamp)으로 넣어야 H2 시간대 변환이 claim 쿼리 파라미터와 맞는다.
        jdbcTemplate.update("UPDATE reports SET created_at = ? WHERE id = ?", Timestamp.valueOf(createdAt), report.getId());
        myReports.add(report.getId());
        return reportRepository.findById(report.getId()).orElseThrow();
    }
}
