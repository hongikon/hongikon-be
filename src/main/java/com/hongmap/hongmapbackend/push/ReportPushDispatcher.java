package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.notification.NewReportScope;
import com.hongmap.hongmapbackend.notification.ReportKeywordCandidate;
import com.hongmap.hongmapbackend.notification.ReportKeywordPushLog;
import com.hongmap.hongmapbackend.notification.ReportKeywordPushLogRepository;
import com.hongmap.hongmapbackend.notification.ReportKeywordSubscriptionRepository;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.report.ReportModeratedEvent;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.regex.Pattern;

/**
 * 제보 검토 결과 푸시(Expo). 관리자 PATCH /admin/reports/{id} 가 커밋된 뒤 별도 스레드에서 돈다 — 관리자 응답을 늦추지 않고,
 * 발송 실패는 로그만 남긴다.
 *
 * <ul>
 *   <li>REPORT_STATUS — 작성자에게 "제보가 지도에 올라갔어요"(ACTIVE) / "제보가 반려됐어요"(REJECTED, 사유 포함).
 *       user_notification_settings.report_status_enabled = false인 유저만 빼고 보낸다(행이 없으면 켜짐).</li>
 *   <li>REPORT_NEW — 제보가 처음 지도에 올라갈 때(PENDING·REJECTED → ACTIVE) 새 제보 알림을 켠 다른 유저에게.
 *       숨김 해제(HIDDEN → ACTIVE)는 "새" 제보가 아니라 보내지 않는다. 기본 꺼짐이라 행이 있고 켠 유저만 받는다.
 *       유저마다 push.report-new-throttle-minutes(기본 30분)에 한 번까지 — new_report_last_sent_at으로 제한한다.
 *       범위가 KEYWORDS 인 유저는 이 일반 알림을 받지 않는다.</li>
 *   <li>REPORT_NEW(제보 키워드) — 위 일반 알림보다 먼저, 새 제보 알림을 켠 유저(범위 무관) 중 제보 키워드
 *       (report_keyword_subscriptions)가 제목·본문·장소 설명·직접 입력 분류·건물명에 들어간(대소문자·공백 무시) 유저에게
 *       빈도 제한 없이 "[간식] 새 제보 · 홍문관 1층" 으로 보낸다. 보낸 유저는 report_keyword_push_log 에 남겨 같은 제보로
 *       다시 받지 않고(스케줄러 재선택 포함) 같은 제보의 일반 알림에서도 빠지며, new_report_last_sent_at 도 갱신된다.</li>
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
    private final ReportKeywordSubscriptionRepository reportKeywordRepository;
    private final ReportKeywordPushLogRepository reportKeywordPushLogRepository;
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

    /** 제보 키워드 알림(빈도 제한 없음)을 먼저, 그다음 범위 CAMPUS 유저의 일반 새 제보 알림을 보낸다. */
    private int sendNewReport(ReportModeratedEvent event) {
        LocalDateTime keywordSentAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        int accepted = sendKeywordMatches(event, keywordSentAt);
        return accepted + sendCampusNewReport(event, keywordSentAt);
    }

    private int sendKeywordMatches(ReportModeratedEvent event, LocalDateTime sentAt) {
        List<ReportKeywordCandidate> candidates =
                reportKeywordRepository.findPushCandidates(event.reportId(), event.authorId());
        if (candidates.isEmpty()) {
            return 0;
        }
        Map<Long, List<String>> matched = matchKeywords(candidates, matchTexts(event));
        if (matched.isEmpty()) {
            return 0;
        }

        // (report_id, user_id) 유일 기록을 먼저 남긴 유저에게만 보낸다 — 동시에 두 번 돌아도 한 번만 간다.
        Map<Long, List<String>> claimed = new LinkedHashMap<>();
        for (Map.Entry<Long, List<String>> entry : matched.entrySet()) {
            try {
                reportKeywordPushLogRepository.saveAndFlush(new ReportKeywordPushLog(event.reportId(), entry.getKey()));
                claimed.put(entry.getKey(), entry.getValue());
            } catch (DataIntegrityViolationException e) {
                // 이미 이 제보로 키워드 알림을 받은 유저
            }
        }
        if (claimed.isEmpty()) {
            return 0;
        }
        settingRepository.markNewReportSent(claimed.keySet(), sentAt);

        String place = place(event);
        Map<String, Object> data = Map.of("type", DATA_TYPE_REPORT_NEW, "reportId", event.reportId());
        List<ExpoPushMessage> messages = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object[] row : userDeviceRepository.findActiveTokensByUserIds(TokenType.EXPO, claimed.keySet())) {
            Long userId = (Long) row[0];
            String token = (String) row[1];
            if (seen.add(token)) {
                messages.add(ExpoPushMessage.of(token, keywordTitle(claimed.get(userId), place), event.title(), data));
            }
        }
        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        // 키워드 내용은 개인 설정이라 로그에 남기지 않는다(건수만).
        log.info("제보 키워드 푸시: reportId={}, 대상 유저 {}명, 메시지 {}건 중 {}건 접수",
                event.reportId(), claimed.size(), messages.size(), result.accepted());
        return result.accepted();
    }

    private int sendCampusNewReport(ReportModeratedEvent event, LocalDateTime keywordSentAt) {
        // DB(datetime(6))에 저장되는 값과 정확히 같아야 선점한 행을 다시 찾을 수 있어 마이크로초로 자른다.
        // 키워드 알림 유저에게 찍은 시각과 겹치면 그 유저까지 대상 기기로 읽히므로 반드시 다른 값을 쓴다.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        if (!now.isAfter(keywordSentAt)) {
            now = keywordSentAt.plus(1, ChronoUnit.MICROS);
        }
        LocalDateTime cutoff = now.minusMinutes(properties.getReportNewThrottleMinutes());
        int claimed = settingRepository.claimNewReportRecipients(
                NewReportScope.CAMPUS, event.reportId(), event.authorId(), now, cutoff);
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

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** 대소문자·공백 무시 비교용. "간식 행사" → "간식행사". */
    static String normalize(String text) {
        return text == null ? "" : WHITESPACE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** 키워드를 찾을 곳 — 제목·본문·장소 설명·직접 입력 분류·건물명(각각 따로 본다: 필드 경계를 넘는 우연한 일치 방지). */
    static List<String> matchTexts(ReportModeratedEvent event) {
        List<String> texts = new ArrayList<>();
        for (String field : new String[]{event.title(), event.content(), event.placeLabel(),
                event.customCategoryLabel(), event.buildingName()}) {
            String normalized = normalize(field);
            if (!normalized.isEmpty()) {
                texts.add(normalized);
            }
        }
        return texts;
    }

    /** 유저별로 걸린 키워드(후보 순서 = 등록 순서). 걸린 게 없는 유저는 빠진다. */
    static Map<Long, List<String>> matchKeywords(List<ReportKeywordCandidate> candidates, List<String> texts) {
        Map<Long, List<String>> matched = new LinkedHashMap<>();
        for (ReportKeywordCandidate candidate : candidates) {
            String keyword = normalize(candidate.keyword());
            if (keyword.isEmpty()) {
                continue;
            }
            for (String text : texts) {
                if (text.contains(keyword)) {
                    matched.computeIfAbsent(candidate.userId(), id -> new ArrayList<>()).add(candidate.keyword().trim());
                    break;
                }
            }
        }
        return matched;
    }

    /** "[간식] 새 제보 · 홍문관 1층", 여러 개면 "[간식 외 1] 새 제보 · 홍문관 1층". */
    static String keywordTitle(List<String> keywords, String place) {
        String label = keywords.size() == 1 ? keywords.get(0) : keywords.get(0) + " 외 " + (keywords.size() - 1);
        return "[" + label + "] 새 제보 · " + place;
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
        return place(event.buildingName(), event.floor());
    }

    /** 관리자 알림(AdminAlertDispatcher)도 같은 형식을 쓴다. */
    static String place(String buildingName, Integer floorNumber) {
        String floor = floorNumber == null ? "" : floorNumber < 0 ? "B" + (-floorNumber) + "층" : floorNumber + "층";
        String building = buildingName == null ? "" : buildingName;
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
