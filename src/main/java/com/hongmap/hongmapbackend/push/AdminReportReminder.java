package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 승인 대기(PENDING) 제보 리마인드 — 제보가 오래 검토되지 않으면 관리자에게 다시 알린다.
 *
 * <ul>
 *   <li>주기: push.admin-reminder-cron(기본 10분마다). 한 회차에 푸시는 관리자 기기마다 한 번(제보마다가 아니라 묶어서).</li>
 *   <li>대상 제보: 아직 끝나지 않은(ends_at &gt; 지금) PENDING 이 30분(push.admin-reminder-after-minutes) 넘은 제보 → 1회, 2시간(push.admin-reminder-repeat-after-minutes)
 *       넘으면 2회째. 제보 하나는 최대 2번까지만 리마인드에 들어간다(reports.admin_reminder_count).
 *       선점은 DB 조건부 UPDATE(ReportRepository.claimAdminReminders) — 서버가 여러 대이거나 재시작해도 두 번 보내지 않는다.</li>
 *   <li>방해 금지: KST 00:00–08:00(push.admin-reminder-quiet-start-hour/end-hour)에는 선점도 발송도 하지 않는다.
 *       그 사이 쌓인 건은 08:00 회차에 한 번에 요약된다.</li>
 *   <li>받는 사람·끄기·채널·토큰 마스킹은 AdminAlertDispatcher 와 같다(role=ADMIN 활성 Expo 기기, admin_alerts_enabled=false 제외,
 *       Android 채널 "admin"). 받을 기기가 하나도 없으면 선점하지 않고 넘어간다(관리자가 기기를 등록하면 그때 보냄).</li>
 *   <li>내용: "[관리] 검토 대기 중인 제보가 N건 있어요" / "가장 오래된 것 M분 전",
 *       data {type: ADMIN_REPORT_REMINDER, count: N, oldestReportId}. N은 30분 넘게 대기 중인 PENDING 제보 수.</li>
 * </ul>
 * 서버 시각(LocalDateTime)은 UTC 다(HongmapBackendApplication) — 방해 금지 판단만 KST 로 바꿔 본다.
 */
@Slf4j
@Component
public class AdminReportReminder {

    static final String DATA_TYPE = "ADMIN_REPORT_REMINDER";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReportRepository reportRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final ExpoPushSender expoPushSender;
    private final PushProperties pushProperties;
    private final Clock clock;

    @Value("${push.admin-reminder-after-minutes:30}")
    long afterMinutes = 30;

    @Value("${push.admin-reminder-repeat-after-minutes:120}")
    long repeatAfterMinutes = 120;

    @Value("${push.admin-reminder-quiet-start-hour:0}")
    int quietStartHour = 0;

    @Value("${push.admin-reminder-quiet-end-hour:8}")
    int quietEndHour = 8;

    @Autowired
    public AdminReportReminder(ReportRepository reportRepository, UserDeviceRepository userDeviceRepository,
                               ExpoPushSender expoPushSender, PushProperties pushProperties) {
        this(reportRepository, userDeviceRepository, expoPushSender, pushProperties, Clock.systemUTC());
    }

    AdminReportReminder(ReportRepository reportRepository, UserDeviceRepository userDeviceRepository,
                        ExpoPushSender expoPushSender, PushProperties pushProperties, Clock clock) {
        this.reportRepository = reportRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.expoPushSender = expoPushSender;
        this.pushProperties = pushProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "${push.admin-reminder-cron:0 */10 * * * *}")
    public void scheduled() {
        try {
            remind();
        } catch (Exception e) {
            log.warn("승인 대기 리마인드 실패: {}", PushTokenMasker.maskWithin(e.getMessage()));
        }
    }

    /** 한 회차. Expo 가 받아들인 메시지 수를 돌려준다(보내지 않았으면 0). */
    int remind() {
        if (!pushProperties.isEnabled()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (isQuietHours(now)) {
            return 0;
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (UserDevice device : userDeviceRepository.findAdminAlertTargets(TokenType.EXPO, UserRole.ADMIN, null)) {
            tokens.add(device.getPushToken());
        }
        if (tokens.isEmpty()) {
            return 0;
        }
        LocalDateTime firstCutoff = now.minusMinutes(afterMinutes);
        int claimed = reportRepository.claimAdminReminders(now, firstCutoff, now.minusMinutes(repeatAfterMinutes),
                now.minusMinutes(Math.max(0, repeatAfterMinutes - afterMinutes)));
        if (claimed == 0) {
            return 0;
        }
        // 이미 끝난 PENDING 제보는 승인해도 지도에 뜨지 않으니 개수·"가장 오래된 것"에서 뺀다(선점 쿼리도 같은 조건).
        long count = reportRepository.countByStatusAndCreatedAtLessThanEqualAndEndsAtAfter(
                ReportStatus.PENDING, firstCutoff, now);
        Optional<Report> oldest = reportRepository.findFirstByStatusAndEndsAtAfterOrderByCreatedAtAscIdAsc(
                ReportStatus.PENDING, now);
        if (count == 0 || oldest.isEmpty()) {
            return 0; // 선점과 조회 사이에 모두 처리됨
        }
        long oldestMinutes = Math.max(0, Duration.between(oldest.get().getCreatedAt(), now).toMinutes());

        String title = AdminAlertDispatcher.TITLE_PREFIX + "검토 대기 중인 제보가 " + count + "건 있어요";
        String body = "가장 오래된 것 " + ago(oldestMinutes);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", DATA_TYPE);
        data.put("count", count);
        data.put("oldestReportId", oldest.get().getId());

        List<ExpoPushMessage> messages = new ArrayList<>();
        for (String token : tokens) {
            messages.add(ExpoPushMessage.of(token, title, body, data)
                    .withChannel(AdminAlertDispatcher.ANDROID_CHANNEL, AdminAlertDispatcher.CATEGORY));
        }
        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        log.info("승인 대기 리마인드 푸시: 이번 회차 {}건 선점, 대기 {}건(가장 오래된 id={} {}분), 메시지 {}건 중 {}건 접수",
                claimed, count, oldest.get().getId(), oldestMinutes, messages.size(), result.accepted());
        return result.accepted();
    }

    /** now(UTC)가 KST 방해 금지 시간대인지. start > end 면 자정을 넘는 구간(예: 22–7)으로 본다. start == end 면 끔. */
    boolean isQuietHours(LocalDateTime nowUtc) {
        if (quietStartHour == quietEndHour) {
            return false;
        }
        LocalTime kst = nowUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).toLocalTime();
        LocalTime start = LocalTime.of(quietStartHour, 0);
        LocalTime end = LocalTime.of(quietEndHour, 0);
        if (start.isBefore(end)) {
            return !kst.isBefore(start) && kst.isBefore(end);
        }
        return !kst.isBefore(start) || kst.isBefore(end);
    }

    /** "35분 전", "2시간 전", "2시간 5분 전". */
    static String ago(long minutes) {
        if (minutes < 60) {
            return minutes + "분 전";
        }
        long h = minutes / 60;
        long m = minutes % 60;
        return m == 0 ? h + "시간 전" : h + "시간 " + m + "분 전";
    }
}
