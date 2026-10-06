package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.admin.AdminReportService;
import com.hongmap.hongmapbackend.admin.dto.ReportModerationRequest;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.notification.NewReportDigestRecipient;
import com.hongmap.hongmapbackend.notification.NewReportScope;
import com.hongmap.hongmapbackend.notification.NotificationSettingService;
import com.hongmap.hongmapbackend.notification.ReportKeywordPushLog;
import com.hongmap.hongmapbackend.notification.ReportKeywordPushLogRepository;
import com.hongmap.hongmapbackend.notification.ReportKeywordSubscription;
import com.hongmap.hongmapbackend.notification.ReportKeywordSubscriptionRepository;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.notification.dto.NotificationSettingsUpdateRequest;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportDigestCandidate;
import com.hongmap.hongmapbackend.report.ReportModeratedEvent;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 새 제보 알림 모아 보내기(NewReportDigestScheduler) + 즉시 알림의 빈도 제한·방해 금지 시간(ReportPushDispatcher).
 * 시각은 바꿔 끼운 Clock 으로 움직이고 runOnce()·dispatch()를 직접 부른다. 방해 금지 시간은 이 클래스만 23~8시로 켠다.
 * 스케줄러는 전체 유저·제보를 보므로 테스트마다 며칠씩 떨어진 먼 미래 날짜를 쓰고, 이 테스트의 토큰(run 접두어)만 확인한다.
 */
@SpringBootTest(properties = {"push.report-new-quiet-start=23", "push.report-new-quiet-end=8"})
@ActiveProfiles("test")
@Import(NewReportDigestTest.TestClockConfig.class)
class NewReportDigestTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final AtomicLong OFFSET_DAYS = new AtomicLong(300);

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    /** 테스트가 움직이는 UTC Clock. */
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());

        void set(LocalDateTime utc) {
            instant.set(utc.toInstant(ZoneOffset.UTC));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    @Autowired MutableClock clock;
    @Autowired NewReportDigestScheduler digest;
    @Autowired ReportPushDispatcher dispatcher;
    @Autowired ReportStartPushScheduler startScheduler;
    @Autowired AdminReportService adminReportService;
    @Autowired NotificationSettingService notificationSettingService;
    @Autowired ReportRepository reportRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired UserNotificationSettingRepository settingRepository;
    @Autowired ReportKeywordSubscriptionRepository reportKeywordRepository;
    @Autowired ReportKeywordPushLogRepository reportKeywordPushLogRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ExpoPushClient expoPushClient;

    String run;
    List<ExpoPushMessage> sent;
    User author;
    Building building;
    String place;
    LocalDate day;

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        sent = Collections.synchronizedList(new ArrayList<>());
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            sent.addAll(batch);
            return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
        });
        author = user();
        building = buildingRepository.save(Building.builder()
                .name("모아관-" + run)
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        place = building.getName() + " 1층";
        day = LocalDate.now(KST).plusDays(OFFSET_DAYS.getAndAdd(3));
    }

    // ---------- 빈도 제한 → 다이제스트 ----------

    @Test
    void 빈도_제한에_걸린_제보는_풀린_뒤_다이제스트로_모두_온다_재실행해도_한_번() {
        String listener = campusListener();

        at(12, 0);
        Report a = published("붕어빵 트럭", kst(12, 0).minusSeconds(1));
        dispatcher.dispatch(approved(a));
        assertThat(to(listener)).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("새 제보 · " + place);
            assertThat(m.body()).isEqualTo("붕어빵 트럭");
        });

        at(12, 10);
        Report b = published("호떡 나눔", kst(12, 10).minusSeconds(1));
        dispatcher.dispatch(approved(b));
        at(12, 20);
        Report c = published("커피 트럭", kst(12, 20).minusSeconds(1));
        dispatcher.dispatch(approved(c));
        assertThat(to(listener)).hasSize(1);   // 빈도 제한 — 바로는 안 온다

        at(12, 25);
        digest.runOnce();
        assertThat(to(listener)).hasSize(1);   // 아직 30분 안

        at(12, 31);
        digest.runOnce();
        assertThat(to(listener)).hasSize(2);
        ExpoPushMessage m = to(listener).get(1);
        assertThat(m.title()).isEqualTo("새 제보 2건");
        assertThat(m.body()).isEqualTo("커피 트럭 외 1건");
        assertThat(m.data()).isEqualTo(Map.of("type", "REPORT_NEW", "reportId", c.getId()));

        // 같은 회차·다음 회차·빈도 제한이 다시 풀린 뒤에도 같은 제보를 또 보내지 않는다.
        digest.runOnce();
        at(12, 36);
        digest.runOnce();
        at(13, 10);
        digest.runOnce();
        assertThat(to(listener)).hasSize(2);
    }

    @Test
    void 다이제스트_1건은_일반_알림과_같은_형식_3건은_N건_형식() {
        String one = campusListener();
        String three = campusListener();
        at(14, 0);
        lastSent(one, kst(13, 25));      // 빈도 제한은 풀렸고, 13:25 뒤에 뜬 제보만 받는다
        lastSent(three, kst(13, 0));

        published("13시 10분 제보", kst(13, 10));
        published("13시 20분 제보", kst(13, 20));
        Report latest = published("떡볶이 무료 시식 — 학생회관 앞에서 선착순 백 명에게 나눠 드려요 서두르세요", kst(13, 40));

        digest.runOnce();

        assertThat(to(one)).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("새 제보 · " + place);
            assertThat(m.body()).isEqualTo(latest.getTitle());
            assertThat(m.data()).isEqualTo(Map.of("type", "REPORT_NEW", "reportId", latest.getId()));
        });
        assertThat(to(three)).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("새 제보 3건");
            assertThat(m.body()).isEqualTo("떡볶이 무료 시식 — 학생회관 앞에서 선착순 백 명에게 나눠 드려요 서… 외 2건");
            assertThat(m.data()).isEqualTo(Map.of("type", "REPORT_NEW", "reportId", latest.getId()));
        });
    }

    @Test
    void 형식_도우미() {
        ReportDigestCandidate x = new ReportDigestCandidate(2L, 9L, "  짧은 제목 ", "홍문관", -1, LocalDateTime.now());
        ReportDigestCandidate y = new ReportDigestCandidate(1L, 9L, "이전", null, null, LocalDateTime.now());
        assertThat(NewReportDigestScheduler.title(List.of(x))).isEqualTo("새 제보 · 홍문관 B1층");
        assertThat(NewReportDigestScheduler.title(List.of(y))).isEqualTo("새 제보 · 캠퍼스");
        assertThat(NewReportDigestScheduler.body(List.of(x, y))).isEqualTo("짧은 제목 외 1건");
        assertThat(NewReportDigestScheduler.shorten("가".repeat(40))).isEqualTo("가".repeat(40));
        assertThat(NewReportDigestScheduler.shorten("가".repeat(41))).isEqualTo("가".repeat(39) + "…");

        LocalDateTime floor = LocalDateTime.of(2026, 10, 6, 0, 0);
        assertThat(NewReportDigestScheduler.windowStart(new NewReportDigestRecipient(1L, null, null), floor)).isEqualTo(floor);
        assertThat(NewReportDigestScheduler.windowStart(
                new NewReportDigestRecipient(1L, null, floor.plusHours(1)), floor)).isEqualTo(floor.plusHours(1));
        assertThat(NewReportDigestScheduler.windowStart(
                new NewReportDigestRecipient(1L, floor.minusDays(1), floor.plusHours(1)), floor)).isEqualTo(floor);
    }

    // ---------- 방해 금지 시간 ----------

    @Test
    void 방해_금지_시간에는_일반_알림도_다이제스트도_없고_8시_첫_회차에_모아_보낸다_키워드는_바로() {
        String listener = campusListener();
        String keywordOnly = subscriber(NewReportScope.KEYWORDS, "야식");
        String keywordsNoMatch = subscriber(NewReportScope.KEYWORDS, "없는말");

        at(23, 30);
        Report night = published("야식 나눔", kst(23, 30).minusSeconds(1));
        dispatcher.dispatch(approved(night));
        assertThat(to(listener)).isEmpty();
        assertThat(to(keywordOnly)).singleElement()
                .satisfies(m -> assertThat(m.title()).isEqualTo("[야식] 새 제보 · " + place));

        at(23, 35);
        digest.runOnce();
        Report dawn = published("새벽 택배 정리", nextDayKst(3, 30));
        clock.set(nextDayKst(7, 55));
        digest.runOnce();
        assertThat(to(listener)).isEmpty();

        clock.set(nextDayKst(8, 0).plusSeconds(1));
        digest.runOnce();
        assertThat(to(listener)).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("새 제보 2건");
            assertThat(m.body()).isEqualTo("새벽 택배 정리 외 1건");
            assertThat(m.data()).containsEntry("reportId", dawn.getId());
        });
        // KEYWORDS 범위는 다이제스트를 받지 않는다(키워드 알림 1건뿐).
        assertThat(to(keywordOnly)).hasSize(1);
        assertThat(to(keywordsNoMatch)).isEmpty();
    }

    @Test
    void 방해_금지_설정값() {
        PushProperties p = new PushProperties(true, "u", "", 1, 1, 3, 30, 120, 23, 8);
        assertThat(p.isReportNewQuietTime(utc(22, 59))).isFalse();
        assertThat(p.isReportNewQuietTime(utc(23, 0))).isTrue();
        assertThat(p.isReportNewQuietTime(utc(7, 59))).isTrue();
        assertThat(p.isReportNewQuietTime(utc(8, 0))).isFalse();
        PushProperties off = new PushProperties(true, "u", "", 1, 1, 3, 30, 120, 0, 0);
        assertThat(off.isReportNewQuietTime(utc(3, 0))).isFalse();
        PushProperties daytime = new PushProperties(true, "u", "", 1, 1, 3, 30, 120, 9, 18);
        assertThat(daytime.isReportNewQuietTime(utc(12, 0))).isTrue();
        assertThat(daytime.isReportNewQuietTime(utc(18, 0))).isFalse();
    }

    // ---------- 제외 ----------

    @Test
    void 키워드_알림으로_받은_제보는_다이제스트에서_빠진다() {
        String listener = campusListener();
        at(15, 0);
        lastSent(listener, kst(14, 0));
        Report keyword = published("간식 나눔", kst(14, 30));
        Report plain = published("우산 대여", kst(14, 40));
        reportKeywordPushLogRepository.save(new ReportKeywordPushLog(keyword.getId(), ownerOf(listener).getId()));

        digest.runOnce();

        assertThat(to(listener)).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("새 제보 · " + place);
            assertThat(m.body()).isEqualTo("우산 대여");
            assertThat(m.data()).containsEntry("reportId", plain.getId());
        });
    }

    @Test
    void 끝난_숨긴_내_제보와_아직_공개_전_제보는_빠지고_KEYWORDS_범위와_알림_끈_유저는_받지_않는다() {
        String listener = campusListener();
        User me = ownerOf(listener);
        String keywords = subscriber(NewReportScope.KEYWORDS);
        User off = user();
        String offToken = device(off);
        optIn(off, NewReportScope.CAMPUS, false);
        at(16, 0);
        for (String token : List.of(listener, keywords, offToken)) {
            lastSent(token, kst(14, 0));
        }

        Report ok = published("살아 있는 제보", kst(15, 0));
        Report ended = published("끝난 제보", kst(15, 10));
        jdbcTemplate.update("UPDATE reports SET ends_at = ? WHERE id = ?", kst(15, 50), ended.getId());
        Report hidden = published("숨긴 제보", kst(15, 20));
        jdbcTemplate.update("UPDATE reports SET status = 'HIDDEN' WHERE id = ?", hidden.getId());
        reportRepository.save(builder(me, "내 제보", kst(15, 30)).build());
        reportRepository.save(builder(author, "공개 전 예정 제보", null).build());

        digest.runOnce();

        assertThat(to(listener)).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("새 제보 · " + place);
            assertThat(m.body()).isEqualTo("살아 있는 제보");
            assertThat(m.data()).containsEntry("reportId", ok.getId());
        });
        assertThat(to(keywords)).isEmpty();
        assertThat(to(offToken)).isEmpty();
    }

    // ---------- published_at 기록 ----------

    @Test
    void 승인하면_공개_시각이_남고_숨김_해제는_새_제보로_다시_묶이지_않는다() {
        clock.set(LocalDateTime.now(ZoneOffset.UTC));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Report live = reportRepository.save(builder(author, "승인 대기", null)
                .status(ReportStatus.PENDING).startsAt(now.minusMinutes(5)).endsAt(now.plusHours(2)).build());
        Report scheduled = reportRepository.save(builder(author, "예정", null)
                .status(ReportStatus.PENDING).startsAt(now.plusHours(1)).endsAt(now.plusHours(3)).build());

        adminReportService.moderate(live.getId(), new ReportModerationRequest("ACTIVE", null));
        adminReportService.moderate(scheduled.getId(), new ReportModerationRequest("ACTIVE", null));
        LocalDateTime firstPublished = reportRepository.findById(live.getId()).orElseThrow().getPublishedAt();
        assertThat(firstPublished).isNotNull();
        assertThat(reportRepository.findById(scheduled.getId()).orElseThrow().getPublishedAt()).isNull();

        adminReportService.moderate(live.getId(), new ReportModerationRequest("HIDDEN", null));
        adminReportService.moderate(live.getId(), new ReportModerationRequest("ACTIVE", null));
        assertThat(reportRepository.findById(live.getId()).orElseThrow().getPublishedAt()).isEqualTo(firstPublished);
    }

    @Test
    void 예정_제보는_시작_알림을_보낼_때_공개_시각이_남고_다시_집혀도_한_번만_보낸다() {
        String listener = campusListener();
        LocalDateTime t0 = kst(10, 0);
        clock.set(t0);
        Report early = reportRepository.save(builder(author, "예정 부스", null)
                .startsAt(t0.plusMinutes(2)).endsAt(t0.plusHours(3)).reviewedAt(t0.minusHours(1)).build());

        startScheduler.resetCheckedUntil(t0);
        startScheduler.runUntil(t0.plusMinutes(3));
        startScheduler.resetCheckedUntil(t0);   // 재시작 lookback 처럼 같은 구간을 다시 본다
        lastSent(listener, null);
        startScheduler.runUntil(t0.plusMinutes(3));

        assertThat(to(listener)).singleElement()
                .satisfies(m -> assertThat(m.data()).containsEntry("reportId", early.getId()));
        assertThat(reportRepository.findById(early.getId()).orElseThrow().getPublishedAt()).isEqualTo(t0.plusMinutes(3));
    }

    @Test
    void 캠퍼스_새_제보_알림을_새로_켜면_마지막_발송_시각을_비운다() {
        User user = user();
        notificationSettingService.update(user.getId(), new NotificationSettingsUpdateRequest(null, true, "KEYWORDS", null));
        jdbcTemplate.update("UPDATE user_notification_settings SET new_report_last_sent_at = ? WHERE user_id = ?",
                LocalDateTime.now().minusHours(3), user.getId());

        notificationSettingService.update(user.getId(), new NotificationSettingsUpdateRequest(null, null, "CAMPUS", null));
        assertThat(settingRepository.findById(user.getId()).orElseThrow().getNewReportLastSentAt()).isNull();

        // 이미 캠퍼스 알림을 받는 중이면(다른 설정만 바꿈) 그대로 둔다.
        LocalDateTime sentAt = LocalDateTime.of(2026, 10, 6, 1, 2, 3);
        jdbcTemplate.update("UPDATE user_notification_settings SET new_report_last_sent_at = ? WHERE user_id = ?",
                sentAt, user.getId());
        notificationSettingService.update(user.getId(), new NotificationSettingsUpdateRequest(false, null, null, null));
        assertThat(settingRepository.findById(user.getId()).orElseThrow().getNewReportLastSentAt()).isEqualTo(sentAt);
    }

    // ---------- 픽스처 ----------

    /** 이 테스트 날짜의 KST 시각 → UTC LocalDateTime. */
    private LocalDateTime kst(int hour, int minute) {
        return LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(KST)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** 다음 날(밤을 넘긴 시나리오)의 KST 시각. */
    private LocalDateTime nextDayKst(int hour, int minute) {
        return kst(hour, minute).plusDays(1);
    }

    private void at(int hour, int minute) {
        clock.set(kst(hour, minute));
    }

    private static LocalDateTime utc(int kstHour, int minute) {
        return LocalDateTime.of(LocalDate.of(2026, 10, 6), LocalTime.of(kstHour, minute)).atZone(KST)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** 지금 지도에 떠 있는(ACTIVE) 제보 — publishedAt 에 새 제보로 공개됐다. */
    private Report published(String title, LocalDateTime publishedAt) {
        return reportRepository.save(builder(author, title, publishedAt).build());
    }

    private Report.ReportBuilder builder(User owner, String title, LocalDateTime publishedAt) {
        LocalDateTime base = publishedAt != null ? publishedAt : kst(12, 0);
        return Report.builder()
                .user(owner).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title(title)
                .startsAt(base.minusHours(1)).endsAt(base.plusHours(12))
                .status(ReportStatus.ACTIVE).reviewedAt(base).publishedAt(publishedAt);
    }

    private ReportModeratedEvent approved(Report report) {
        return new ReportModeratedEvent(report.getId(), author.getId(), report.getTitle(), building.getName(), 1,
                ReportStatus.PENDING, ReportStatus.ACTIVE, null, report.getEndsAt(), report.getStartsAt());
    }

    private String campusListener() {
        return subscriber(NewReportScope.CAMPUS);
    }

    private String subscriber(NewReportScope scope, String... words) {
        User user = user();
        String token = device(user);
        for (String word : words) {
            reportKeywordRepository.save(ReportKeywordSubscription.builder().user(user).keyword(word).build());
        }
        optIn(user, scope, true);
        return token;
    }

    private void optIn(User user, NewReportScope scope, boolean enabled) {
        UserNotificationSetting setting = new UserNotificationSetting(user.getId());
        setting.changeNewReportsEnabled(enabled);
        setting.changeNewReportsScope(scope);
        settingRepository.save(setting);
    }

    private void lastSent(String token, LocalDateTime at) {
        jdbcTemplate.update("UPDATE user_notification_settings SET new_report_last_sent_at = ? WHERE user_id = ?",
                at, ownerOf(token).getId());
    }

    private User ownerOf(String token) {
        Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM user_devices WHERE push_token = ?", Long.class, token);
        return userRepository.findById(userId).orElseThrow();
    }

    private List<ExpoPushMessage> to(String token) {
        synchronized (sent) {
            return sent.stream().filter(m -> m.to().equals(token)).toList();
        }
    }

    private User user() {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
    }

    private String device(User user) {
        String token = "ExponentPushToken[" + run + "-" + UUID.randomUUID() + "]";
        userDeviceRepository.save(UserDevice.builder().user(user).pushToken(token).tokenType(TokenType.EXPO).build());
        return token;
    }
}
