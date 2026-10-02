package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.notification.NewReportScope;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.report.ReportModeratedEvent;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 제보 검토 결과 푸시(Expo). 관리자 PATCH /admin/reports/{id} 가 커밋된 뒤 별도 스레드에서 돈다 — 관리자 응답을 늦추지 않고,
 * 발송 실패는 로그만 남긴다.
 *
 * <ul>
 *   <li>REPORT_STATUS — 작성자에게 "제보가 지도에 올라갔어요"(ACTIVE) / "제보가 반려됐어요"(REJECTED, 사유 포함).
 *       user_notification_settings.report_status_enabled = false인 유저만 빼고 보낸다(행이 없으면 켜짐).</li>
 *   <li>REPORT_NEW — 제보가 처음 지도에 올라갈 때(PENDING·REJECTED → ACTIVE) 새 제보 알림을 켠 다른 유저에게.
 *       숨김 해제(HIDDEN → ACTIVE)는 "새" 제보가 아니라 보내지 않는다. 기본 꺼짐이라 행이 있고 켠 유저만 받는다.
 *       유저마다 push.report-new-throttle-minutes(기본 30분)에 한 번까지 — new_report_last_sent_at으로 제한한다.</li>
 * </ul>
 * 승인 시점에 이미 끝난(ends_at 경과) 제보는 지도에 뜨지 않으므로 승인 알림·새 제보 알림 둘 다 보내지 않는다.
 *
 * 예정 제보(승인 시점에 starts_at 이 아직 미래): 작성자에게는 "제보가 승인됐어요 · 10/3(금) 11:00부터 지도에 보여요"를
 * 바로 보내고, 새 제보 알림은 지도에 실제로 뜨는 시작 시각에 ReportStartPushScheduler 가 {@link #dispatchStarted}로 보낸다
 * (알림을 눌렀을 때 지도에 제보가 있어야 해서다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPushDispatcher {

    static final String DATA_TYPE_REPORT_STATUS = "REPORT_STATUS";
    static final String DATA_TYPE_REPORT_NEW = "REPORT_NEW";
    static final String TITLE_APPROVED = "제보가 지도에 올라갔어요";
    static final String TITLE_REJECTED = "제보가 반려됐어요";
    static final String TITLE_APPROVED_SCHEDULED = "제보가 승인됐어요";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KST_FORMAT = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN);
    private static final int MAX_REASON_LENGTH = 80;
    /** 이 상태에서 ACTIVE가 되면 처음 지도에 올라가는 것으로 본다. */
    private static final Set<ReportStatus> FIRST_PUBLISH_FROM = EnumSet.of(ReportStatus.PENDING, ReportStatus.REJECTED);

    private final UserDeviceRepository userDeviceRepository;
    private final UserNotificationSettingRepository settingRepository;
    private final ExpoPushSender expoPushSender;
    private final PushProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportModerated(ReportModeratedEvent event) {
        try {
            dispatch(event);
        } catch (Exception e) {
            log.warn("제보 푸시 실패 (reportId={}, status={}): {}", event.reportId(), event.status(), e.getMessage());
        }
    }

    /** 동기 실행 — 이벤트 리스너와 테스트가 쓴다. Expo가 받아들인 메시지 수를 돌려준다. */
    public int dispatch(ReportModeratedEvent event) {
        if (!properties.isEnabled() || event.status() == event.previousStatus()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean approved = event.status() == ReportStatus.ACTIVE;
        if (approved && event.endsAt() != null && event.endsAt().isBefore(now)) {
            log.debug("이미 끝난 제보라 승인 푸시 생략: reportId={}", event.reportId());
            return 0;
        }
        boolean scheduled = approved && event.startsAfter(now);

        int accepted = 0;
        if (approved || event.status() == ReportStatus.REJECTED) {
            accepted += sendStatus(event, approved, scheduled);
        }
        // 시작 전 제보의 새 제보 알림은 시작 시각에 ReportStartPushScheduler 가 보낸다.
        if (approved && !scheduled && FIRST_PUBLISH_FROM.contains(event.previousStatus())) {
            accepted += sendNewReport(event);
        }
        return accepted;
    }

    /**
     * 시작 전에 승인된 제보가 시작 시각이 되어 지도에 뜰 때 — 미뤄 둔 캠퍼스 새 제보 알림을 보낸다.
     * 유저당 빈도 제한(new_report_last_sent_at)이 같이 걸려, 스케줄러가 같은 제보를 다시 집어도 30분 안엔 중복되지 않는다.
     */
    public int dispatchStarted(ReportModeratedEvent event) {
        if (!properties.isEnabled()) {
            return 0;
        }
        return sendNewReport(event);
    }

    /** UTC LocalDateTime → "10/3(금) 11:00" (KST). */
    static String formatKst(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).format(KST_FORMAT);
    }

    private int sendStatus(ReportModeratedEvent event, boolean approved, boolean scheduled) {
        boolean enabled = settingRepository.findById(event.authorId())
                .map(UserNotificationSetting::isReportStatusEnabled)
                .orElse(UserNotificationSetting.DEFAULT_REPORT_STATUS_ENABLED);
        if (!enabled) {
            return 0;
        }
        String title = scheduled ? TITLE_APPROVED_SCHEDULED : approved ? TITLE_APPROVED : TITLE_REJECTED;
        String body = scheduled ? event.title() + "\n" + formatKst(event.startsAt()) + "부터 지도에 보여요"
                : approved ? event.title() : event.title() + "\n사유: " + truncate(event.note());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", DATA_TYPE_REPORT_STATUS);
        data.put("reportId", event.reportId());
        data.put("status", event.status().name());

        List<ExpoPushMessage> messages = new ArrayList<>();
        for (String token : expoTokens(userDeviceRepository.findByUserIdAndActiveTrue(event.authorId()))) {
            messages.add(ExpoPushMessage.of(token, title, body, data));
        }
        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        log.info("제보 결과 푸시: reportId={}, status={}, 메시지 {}건 중 {}건 접수",
                event.reportId(), event.status(), messages.size(), result.accepted());
        return result.accepted();
    }

    private int sendNewReport(ReportModeratedEvent event) {
        // DB(datetime(6))에 저장되는 값과 정확히 같아야 선점한 행을 다시 찾을 수 있어 마이크로초로 자른다.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime cutoff = now.minusMinutes(properties.getReportNewThrottleMinutes());
        int claimed = settingRepository.claimNewReportRecipients(NewReportScope.CAMPUS, event.authorId(), now, cutoff);
        if (claimed == 0) {
            return 0;
        }

        String title = "새 제보 · " + place(event);
        Map<String, Object> data = Map.of("type", DATA_TYPE_REPORT_NEW, "reportId", event.reportId());
        List<ExpoPushMessage> messages = new ArrayList<>();
        for (String token : expoTokens(userDeviceRepository.findNewReportTargets(TokenType.EXPO, now))) {
            messages.add(ExpoPushMessage.of(token, title, event.title(), data));
        }
        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        log.info("새 제보 푸시: reportId={}, 대상 유저 {}명, 메시지 {}건 중 {}건 접수",
                event.reportId(), claimed, messages.size(), result.accepted());
        return result.accepted();
    }

    private static Set<String> expoTokens(List<UserDevice> devices) {
        Set<String> tokens = new LinkedHashSet<>();
        for (UserDevice device : devices) {
            if (device.isActive() && device.getTokenType() == TokenType.EXPO) {
                tokens.add(device.getPushToken());
            }
        }
        return tokens;
    }

    /** "제2공학관 3층" / "B1층" 형태. 건물명·층이 둘 다 없으면 "캠퍼스". */
    static String place(ReportModeratedEvent event) {
        String floor = event.floor() == null ? "" : event.floor() < 0 ? "B" + (-event.floor()) + "층" : event.floor() + "층";
        String building = event.buildingName() == null ? "" : event.buildingName();
        String place = (building + " " + floor).trim();
        return place.isEmpty() ? "캠퍼스" : place;
    }

    private static String truncate(String note) {
        if (note == null || note.isBlank()) {
            return "-";
        }
        String trimmed = note.trim();
        return trimmed.length() <= MAX_REASON_LENGTH ? trimmed : trimmed.substring(0, MAX_REASON_LENGTH - 1) + "…";
    }
}
