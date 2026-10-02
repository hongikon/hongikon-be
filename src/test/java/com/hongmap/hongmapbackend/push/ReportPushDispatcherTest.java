package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.report.ReportModeratedEvent;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 제보 검토 푸시 — 작성자 결과 알림(REPORT_STATUS)과 캠퍼스 새 제보 알림(REPORT_NEW)의 대상·설정·빈도 제한.
 * dispatch()를 직접 불러 동기로 검증한다(커밋 후 비동기 실행은 NotificationSettingApiIntegrationTest에서).
 * H2 인메모리 DB, Expo API는 ExpoPushClient를 모킹한다. 다른 테스트의 기기와 섞이지 않게 이 테스트의 토큰(run 접두어)만 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportPushDispatcherTest {

    private static final AtomicLong REPORT_IDS = new AtomicLong(900_000);

    @Autowired ReportPushDispatcher dispatcher;
    @Autowired UserRepository userRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired UserNotificationSettingRepository settingRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ExpoPushClient expoPushClient;

    String run;
    List<ExpoPushMessage> sent;

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        sent = Collections.synchronizedList(new ArrayList<>());
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            sent.addAll(batch);
            return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
        });
    }

    // ---------- 내 제보 결과 ----------

    @Test
    void 승인되면_작성자의_활성_Expo_기기에_결과_알림이_간다() {
        User author = user();
        String phone = device(author, TokenType.EXPO, true);
        device(author, TokenType.EXPO, false);
        device(author, TokenType.FCM, true);

        ReportModeratedEvent event = event(author, ReportStatus.PENDING, ReportStatus.ACTIVE, null);
        dispatcher.dispatch(event);

        List<ExpoPushMessage> mine = mine();
        assertThat(mine).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo(phone);
            assertThat(m.title()).isEqualTo("제보가 지도에 올라갔어요");
            assertThat(m.body()).isEqualTo("붕어빵 트럭");
            assertThat(m.data()).isEqualTo(Map.of("type", "REPORT_STATUS", "reportId", event.reportId(), "status", "ACTIVE"));
        });
    }

    @Test
    void 반려되면_사유를_담아_보낸다() {
        User author = user();
        device(author, TokenType.EXPO, true);

        ReportModeratedEvent event = event(author, ReportStatus.PENDING, ReportStatus.REJECTED, "위치가 캠퍼스 밖");
        dispatcher.dispatch(event);

        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("제보가 반려됐어요");
            assertThat(m.body()).isEqualTo("붕어빵 트럭\n사유: 위치가 캠퍼스 밖");
            assertThat(m.data()).containsEntry("status", "REJECTED");
        });
    }

    @Test
    void 결과_알림을_끈_작성자에게는_보내지_않는다() {
        User author = user();
        device(author, TokenType.EXPO, true);
        UserNotificationSetting setting = new UserNotificationSetting(author.getId());
        setting.changeReportStatusEnabled(false);
        settingRepository.save(setting);

        dispatcher.dispatch(event(author, ReportStatus.PENDING, ReportStatus.ACTIVE, null));
        dispatcher.dispatch(event(author, ReportStatus.PENDING, ReportStatus.REJECTED, "중복 제보"));

        assertThat(mine()).isEmpty();
    }

    @Test
    void 숨김_삭제_같은_상태로의_변경과_이미_끝난_제보_승인은_보내지_않는다() {
        User author = user();
        device(author, TokenType.EXPO, true);

        dispatcher.dispatch(event(author, ReportStatus.ACTIVE, ReportStatus.HIDDEN, null));
        dispatcher.dispatch(event(author, ReportStatus.ACTIVE, ReportStatus.DELETED, null));
        dispatcher.dispatch(event(author, ReportStatus.ACTIVE, ReportStatus.ACTIVE, null));
        dispatcher.dispatch(new ReportModeratedEvent(REPORT_IDS.incrementAndGet(), author.getId(), "끝난 행사", "홍문관", 1,
                ReportStatus.PENDING, ReportStatus.ACTIVE, null, LocalDateTime.now().minusMinutes(1)));

        assertThat(mine()).isEmpty();
    }

    @Test
    void 시작_전에_승인되면_시작_시각을_알려주고_새_제보_알림은_미룬다() {
        User author = user();
        String authorToken = device(author, TokenType.EXPO, true);
        User optedIn = user();
        String optedInToken = device(optedIn, TokenType.EXPO, true);
        optInNewReports(optedIn);

        // 내일 02:00 UTC(= 11:00 KST). 승인 시점에 아직 시작 전이어야 해서 고정 날짜 대신 now 기준으로 만든다.
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1).withHour(2).withMinute(0).withSecond(0).withNano(0);
        ReportModeratedEvent event = new ReportModeratedEvent(REPORT_IDS.incrementAndGet(), author.getId(), "붕어빵 트럭",
                "홍문관", -1, ReportStatus.PENDING, ReportStatus.ACTIVE, null, startsAt.plusHours(4), startsAt);
        dispatcher.dispatch(event);

        assertThat(mine()).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo(authorToken);
            assertThat(m.title()).isEqualTo("제보가 승인됐어요");
            assertThat(m.body()).isEqualTo("붕어빵 트럭\n" + ReportPushDispatcher.formatKst(startsAt) + "부터 지도에 보여요");
            assertThat(m.data()).containsEntry("status", "ACTIVE");
        });
        assertThat(newReportsTo(optedInToken)).isZero();

        // 시작 시각이 되면 스케줄러가 dispatchStarted 로 보낸다.
        dispatcher.dispatchStarted(event);
        assertThat(newReportsTo(optedInToken)).isEqualTo(1);
    }

    @Test
    void 시작_시각은_KST_월_일_요일_시각으로_적는다() {
        assertThat(ReportPushDispatcher.formatKst(LocalDateTime.of(2026, 10, 3, 2, 0))).isEqualTo("10/3(토) 11:00");
        assertThat(ReportPushDispatcher.formatKst(LocalDateTime.of(2026, 10, 2, 15, 30))).isEqualTo("10/3(토) 00:30");
    }

    // ---------- 캠퍼스 새 제보 ----------

    @Test
    void 새_제보_알림은_켠_유저에게만_가고_작성자는_빠진다() {
        User author = user();
        String authorToken = device(author, TokenType.EXPO, true);
        optInNewReports(author);

        User optedIn = user();
        String optedInToken = device(optedIn, TokenType.EXPO, true);
        optInNewReports(optedIn);

        User defaultUser = user(); // 설정 행 없음 → 기본 꺼짐
        device(defaultUser, TokenType.EXPO, true);

        User optedOut = user();
        device(optedOut, TokenType.EXPO, true);
        UserNotificationSetting off = new UserNotificationSetting(optedOut.getId());
        off.changeNewReportsEnabled(false);
        settingRepository.save(off);

        ReportModeratedEvent event = event(author, ReportStatus.PENDING, ReportStatus.ACTIVE, null);
        dispatcher.dispatch(event);

        List<ExpoPushMessage> news = mine().stream().filter(m -> "REPORT_NEW".equals(m.data().get("type"))).toList();
        assertThat(news).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo(optedInToken);
            assertThat(m.title()).isEqualTo("새 제보 · 홍문관 B1층");
            assertThat(m.body()).isEqualTo("붕어빵 트럭");
            assertThat(m.data()).isEqualTo(Map.of("type", "REPORT_NEW", "reportId", event.reportId()));
        });
        // 작성자는 결과 알림만 받는다.
        assertThat(mine()).filteredOn(m -> m.to().equals(authorToken))
                .extracting(m -> m.data().get("type")).containsExactly("REPORT_STATUS");
    }

    @Test
    void 새_제보_알림은_유저당_빈도_제한이_있다() {
        User author = user();
        User optedIn = user();
        String token = device(optedIn, TokenType.EXPO, true);
        optInNewReports(optedIn);

        dispatcher.dispatch(event(author, ReportStatus.PENDING, ReportStatus.ACTIVE, null));
        dispatcher.dispatch(event(author, ReportStatus.PENDING, ReportStatus.ACTIVE, null));
        assertThat(newReportsTo(token)).isEqualTo(1);

        // 제한 시간(30분)이 지나면 다시 받는다.
        jdbcTemplate.update("UPDATE user_notification_settings SET new_report_last_sent_at = ? WHERE user_id = ?",
                LocalDateTime.now().minusMinutes(31), optedIn.getId());
        dispatcher.dispatch(event(author, ReportStatus.PENDING, ReportStatus.ACTIVE, null));
        assertThat(newReportsTo(token)).isEqualTo(2);
    }

    @Test
    void 숨김_해제로_다시_올라온_제보는_새_제보_알림을_보내지_않는다() {
        User author = user();
        User optedIn = user();
        String token = device(optedIn, TokenType.EXPO, true);
        optInNewReports(optedIn);

        dispatcher.dispatch(event(author, ReportStatus.HIDDEN, ReportStatus.ACTIVE, null));
        assertThat(newReportsTo(token)).isZero();

        // 반려됐다가 승인된 제보는 처음 지도에 올라가는 것이라 보낸다.
        dispatcher.dispatch(event(author, ReportStatus.REJECTED, ReportStatus.ACTIVE, null));
        assertThat(newReportsTo(token)).isEqualTo(1);
    }

    @Test
    void DeviceNotRegistered_기기는_비활성화한다() {
        User author = user();
        String dead = device(author, TokenType.EXPO, true);
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            return batch.stream()
                    .map(m -> m.to().equals(dead)
                            ? new ExpoPushTicket("error", null, "not registered", Map.of("error", "DeviceNotRegistered"))
                            : new ExpoPushTicket("ok", "ticket-id", null, null))
                    .toList();
        });

        dispatcher.dispatch(event(author, ReportStatus.PENDING, ReportStatus.REJECTED, "중복"));

        assertThat(userDeviceRepository.findByPushToken(dead).orElseThrow().isActive()).isFalse();
    }

    // ---------- 픽스처 ----------

    private List<ExpoPushMessage> mine() {
        synchronized (sent) {
            return sent.stream().filter(m -> m.to().contains(run)).toList();
        }
    }

    private long newReportsTo(String token) {
        return mine().stream().filter(m -> m.to().equals(token) && "REPORT_NEW".equals(m.data().get("type"))).count();
    }

    private ReportModeratedEvent event(User author, ReportStatus previous, ReportStatus status, String note) {
        return new ReportModeratedEvent(REPORT_IDS.incrementAndGet(), author.getId(), "붕어빵 트럭", "홍문관", -1,
                previous, status, note, LocalDateTime.now().plusHours(2));
    }

    private void optInNewReports(User user) {
        UserNotificationSetting setting = new UserNotificationSetting(user.getId());
        setting.changeNewReportsEnabled(true);
        settingRepository.save(setting);
    }

    private User user() {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
    }

    private String device(User user, TokenType tokenType, boolean active) {
        String token = "ExponentPushToken[" + run + "-" + UUID.randomUUID() + "]";
        UserDevice device = UserDevice.builder().user(user).pushToken(token).tokenType(tokenType).build();
        if (!active) {
            device.deactivate();
        }
        userDeviceRepository.save(device);
        return token;
    }
}
