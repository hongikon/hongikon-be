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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 예정 제보: 시작 전에 승인된 제보만 시작 시각에 캠퍼스 새 제보 알림이 간다(ReportStartPushScheduler).
 * runUntil(now)을 직접 불러 확인 구간을 통제한다(정기 실행은 테스트에서 꺼 둠).
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportStartPushSchedulerTest {

    private static final java.util.concurrent.atomic.AtomicLong OFFSET_DAYS = new java.util.concurrent.atomic.AtomicLong();

    @Autowired ReportStartPushScheduler scheduler;
    @Autowired ReportRepository reportRepository;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired UserNotificationSettingRepository settingRepository;

    @MockitoBean ExpoPushClient expoPushClient;

    String run;
    List<ExpoPushMessage> sent;
    User author;
    Building building;
    String listenerToken;

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
                .name("예정알림관-" + run)
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        User listener = user();
        listenerToken = "ExponentPushToken[" + run + "-" + UUID.randomUUID() + "]";
        userDeviceRepository.save(UserDevice.builder().user(listener).pushToken(listenerToken).tokenType(TokenType.EXPO).build());
        UserNotificationSetting setting = new UserNotificationSetting(listener.getId());
        setting.changeNewReportsEnabled(true);
        settingRepository.save(setting);
    }

    @Test
    void 시작_전에_승인된_제보는_시작_시각이_지난_첫_확인에서_한_번만_알린다() {
        LocalDateTime t0 = isolatedNow();
        Report early = report(t0.plusMinutes(5), t0.minusHours(3), ReportStatus.ACTIVE, "붕어빵 트럭");
        scheduler.resetCheckedUntil(t0);

        // 아직 시작 전 — 보내지 않는다.
        scheduler.runUntil(t0.plusMinutes(1));
        assertThat(newReports()).isEmpty();

        // 시작 시각을 지난 확인에서 보낸다.
        scheduler.runUntil(t0.plusMinutes(6));
        assertThat(newReports()).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("새 제보 · " + building.getName() + " 1층");
            assertThat(m.body()).isEqualTo("붕어빵 트럭");
            assertThat(m.data()).isEqualTo(Map.of("type", "REPORT_NEW", "reportId", early.getId()));
        });

        // 다음 확인 구간에는 다시 들어오지 않는다.
        scheduler.runUntil(t0.plusMinutes(7));
        assertThat(newReports()).hasSize(1);
    }

    @Test
    void 여러_날_제보도_시작_알림은_시작할_때_한_번뿐이다() {
        LocalDateTime t0 = isolatedNow();
        Report festival = reportRepository.save(Report.builder()
                .user(author).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("3일 축제 부스")
                .startsAt(t0.plusMinutes(2)).endsAt(t0.plusDays(3)).status(ReportStatus.ACTIVE)
                .reviewedAt(t0.minusDays(1))
                .build());
        scheduler.resetCheckedUntil(t0);

        scheduler.runUntil(t0.plusMinutes(3));
        assertThat(newReports()).singleElement()
                .satisfies(m -> assertThat(m.data()).containsEntry("reportId", festival.getId()));
        // 진행 중인 다음 날들의 확인 구간에도 다시 보내지 않는다(시작 시각이 구간 안에 있을 때만 집는다).
        scheduler.runUntil(t0.plusDays(1));
        scheduler.runUntil(t0.plusDays(2));
        assertThat(newReports()).hasSize(1);
    }

    @Test
    void 시작_뒤에_승인됐거나_승인되지_않은_제보는_보내지_않는다() {
        LocalDateTime t0 = isolatedNow();
        // 시작 뒤 승인 → 승인 때 이미 보냈다.
        report(t0.plusMinutes(2), t0.plusMinutes(3), ReportStatus.ACTIVE, "늦게 승인");
        // 검토 전·숨김 → 지도에 안 뜬다.
        report(t0.plusMinutes(2), null, ReportStatus.PENDING, "검토 전");
        report(t0.plusMinutes(2), t0.minusHours(1), ReportStatus.HIDDEN, "숨김");
        scheduler.resetCheckedUntil(t0);

        scheduler.runUntil(t0.plusMinutes(4));
        assertThat(newReports()).isEmpty();
    }

    /**
     * 스케줄러는 전체 제보를 보므로 테스트끼리 확인 구간이 겹치지 않게 테스트마다 며칠씩 떨어진 "지금"을 쓴다
     * (다른 테스트 클래스의 제보는 이 시각대에 시작하지 않는다).
     */
    private static LocalDateTime isolatedNow() {
        return LocalDateTime.now().plusDays(100 + OFFSET_DAYS.getAndAdd(2));
    }

    private List<ExpoPushMessage> newReports() {
        synchronized (sent) {
            return sent.stream()
                    .filter(m -> m.to().equals(listenerToken) && "REPORT_NEW".equals(m.data().get("type")))
                    .toList();
        }
    }

    private Report report(LocalDateTime startsAt, LocalDateTime reviewedAt, ReportStatus status, String title) {
        return reportRepository.save(Report.builder()
                .user(author).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title(title)
                .startsAt(startsAt).endsAt(startsAt.plusHours(4)).status(status).reviewedAt(reviewedAt)
                .build());
    }

    private User user() {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
    }
}
