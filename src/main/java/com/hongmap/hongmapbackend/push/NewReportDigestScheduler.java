package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.notification.NewReportDigestRecipient;
import com.hongmap.hongmapbackend.notification.NewReportScope;
import com.hongmap.hongmapbackend.notification.ReportKeywordPushLogRepository;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.report.ReportDigestCandidate;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 새 제보 알림 모아 보내기(다이제스트). 캠퍼스 새 제보 일반 알림(ReportPushDispatcher)은 유저마다 30분에 한 번까지이고
 * 방해 금지 시간(KST 23~8시 기본)엔 보내지 않는데, 그 사이에 뜬 제보를 버리지 않고 여기서 모아 보낸다.
 *
 * 5분마다(push.report-new-digest-cron) 방해 금지 시간이 아니면:
 * <ol>
 *   <li>후보 유저 1쿼리 — 새 제보 알림 켬·범위 CAMPUS·빈도 제한 풀림·활성 기기 있음(관리자 알림을 켠 관리자 제외).</li>
 *   <li>유저별 구간 시작 = max(마지막 새 제보 알림 시각, 지금 − 12시간). 받은 적 없으면 마지막 설정 변경(알림을 켠) 시각.</li>
 *   <li>가장 이른 구간 시작 이후 새로 공개된(reports.published_at) 지금도 떠 있는 제보 1쿼리 + 그 제보들의 키워드 발송 기록 1쿼리.</li>
 *   <li>유저별로 자바에서 고른다 — 구간 안, 내 제보 아님, 키워드 알림으로 이미 받은 제보 아님.</li>
 *   <li>보낼 유저를 조건부 UPDATE 로 선점(new_report_last_sent_at)하고, 선점한 유저의 기기 1쿼리로 보낸다 — 서버 여러 대·즉시 알림과
 *       겹쳐도 같은 유저에게 두 번 가지 않고, 다음 회차는 이번 선점 시각 뒤에 뜬 제보만 본다.</li>
 * </ol>
 * 1건이면 일반 알림과 같은 "새 제보 · 홍문관 1층" / 제목, 여러 건이면 "새 제보 3건" / "가장 최근 제목 외 2건".
 * data 는 {type: REPORT_NEW, reportId: 가장 최근 제보} — 앱의 기존 탭 처리 그대로.
 *
 * 선점 시각은 지금보다 {@link #SETTLE} 이전으로 찍고 그 시각까지 공개된 제보만 담는다 — 승인 트랜잭션이 published_at 을 찍고
 * 아직 커밋하기 전인 제보를 이번 회차가 못 보고 구간만 지나쳐 버리는 일을 막는다(그 제보는 다음 회차에 담긴다).
 * 범위 KEYWORDS 유저는 다이제스트를 받지 않는다. 로그에는 건수만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewReportDigestScheduler {

    /** 다이제스트에 담는 가장 오래된 제보 — 밤새·오래 꺼져 있던 유저에게 하루치가 몰려오지 않게. */
    static final Duration LOOKBACK = Duration.ofHours(12);
    static final Duration SETTLE = Duration.ofMinutes(1);
    /** 여러 건 다이제스트 본문의 대표 제목 길이(넘으면 말줄임). */
    static final int MAX_LEAD_TITLE_LENGTH = 40;
    private static final int CLAIM_CHUNK = 500;

    private final UserNotificationSettingRepository settingRepository;
    private final ReportRepository reportRepository;
    private final ReportKeywordPushLogRepository reportKeywordPushLogRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final ExpoPushSender expoPushSender;
    private final PushProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${push.report-new-digest-cron:0 */5 * * * *}")
    public void run() {
        try {
            runOnce();
        } catch (Exception e) {
            log.warn("새 제보 다이제스트 실패: {}", e.getMessage());
        }
    }

    /** 한 회차. 다이제스트를 보낸(선점한) 유저 수를 돌려준다. */
    public int runOnce() {
        if (!properties.isEnabled()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (properties.isReportNewQuietTime(now)) {
            return 0;
        }
        LocalDateTime claimAt = now.minus(SETTLE);
        LocalDateTime cutoff = now.minusMinutes(properties.getReportNewThrottleMinutes());
        LocalDateTime floor = now.minus(LOOKBACK);

        List<NewReportDigestRecipient> recipients =
                settingRepository.findDigestRecipients(NewReportScope.CAMPUS, cutoff, TokenType.EXPO);
        if (recipients.isEmpty()) {
            return 0;
        }
        Map<Long, LocalDateTime> windowStart = new LinkedHashMap<>();
        LocalDateTime earliest = claimAt;
        for (NewReportDigestRecipient recipient : recipients) {
            LocalDateTime start = windowStart(recipient, floor);
            windowStart.put(recipient.userId(), start);
            if (start.isBefore(earliest)) {
                earliest = start;
            }
        }
        if (!earliest.isBefore(claimAt)) {
            return 0;
        }

        List<ReportDigestCandidate> reports = reportRepository.findDigestCandidates(earliest, claimAt, now);
        if (reports.isEmpty()) {
            return 0;
        }
        Map<Long, Set<Long>> keywordSent = keywordSentByUser(reports);

        // 유저별 다이제스트(최근 공개 순 — 후보 쿼리 순서 그대로)
        Map<Long, List<ReportDigestCandidate>> digests = new LinkedHashMap<>();
        for (Map.Entry<Long, LocalDateTime> entry : windowStart.entrySet()) {
            Long userId = entry.getKey();
            Set<Long> already = keywordSent.getOrDefault(userId, Set.of());
            List<ReportDigestCandidate> mine = new ArrayList<>();
            for (ReportDigestCandidate report : reports) {
                if (report.publishedAt().isAfter(entry.getValue())
                        && !userId.equals(report.authorId())
                        && !already.contains(report.reportId())) {
                    mine.add(report);
                }
            }
            if (!mine.isEmpty()) {
                digests.put(userId, mine);
            }
        }
        if (digests.isEmpty()) {
            return 0;
        }

        int claimedUsers = 0;
        List<ExpoPushMessage> messages = new ArrayList<>();
        List<Long> userIds = new ArrayList<>(digests.keySet());
        for (int i = 0; i < userIds.size(); i += CLAIM_CHUNK) {
            List<Long> chunk = userIds.subList(i, Math.min(i + CLAIM_CHUNK, userIds.size()));
            if (settingRepository.claimDigestRecipients(chunk, NewReportScope.CAMPUS, claimAt, cutoff) == 0) {
                continue;
            }
            List<Long> claimed = settingRepository.findUserIdsClaimedAt(chunk, claimAt);
            if (claimed.isEmpty()) {
                continue;
            }
            claimedUsers += claimed.size();
            Set<String> seen = new LinkedHashSet<>();
            for (Object[] row : userDeviceRepository.findActiveTokensByUserIds(TokenType.EXPO, claimed)) {
                Long userId = (Long) row[0];
                String token = (String) row[1];
                if (seen.add(token)) {
                    messages.add(message(token, digests.get(userId)));
                }
            }
        }
        if (claimedUsers == 0) {
            return 0;
        }
        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        log.info("새 제보 다이제스트: 후보 제보 {}건, 대상 유저 {}명, 메시지 {}건 중 {}건 접수",
                reports.size(), claimedUsers, messages.size(), result.accepted());
        return claimedUsers;
    }

    /** max(마지막 발송 시각 — 없으면 설정을 바꾼(알림을 켠) 시각, floor). */
    static LocalDateTime windowStart(NewReportDigestRecipient recipient, LocalDateTime floor) {
        LocalDateTime since = recipient.lastSentAt() != null ? recipient.lastSentAt() : recipient.settingsUpdatedAt();
        return since != null && since.isAfter(floor) ? since : floor;
    }

    private Map<Long, Set<Long>> keywordSentByUser(List<ReportDigestCandidate> reports) {
        List<Long> reportIds = reports.stream().map(ReportDigestCandidate::reportId).toList();
        Map<Long, Set<Long>> byUser = new HashMap<>();
        for (Object[] row : reportKeywordPushLogRepository.findPairsByReportIds(reportIds)) {
            byUser.computeIfAbsent((Long) row[1], id -> new HashSet<>()).add((Long) row[0]);
        }
        return byUser;
    }

    /** reports 는 최근 공개 순(첫 번째가 가장 최근). */
    static ExpoPushMessage message(String token, List<ReportDigestCandidate> reports) {
        ReportDigestCandidate latest = reports.get(0);
        Map<String, Object> data = Map.of("type", ReportPushDispatcher.DATA_TYPE_REPORT_NEW, "reportId", latest.reportId());
        return ExpoPushMessage.of(token, title(reports), body(reports), data);
    }

    /** 1건: "새 제보 · 홍문관 1층"(일반 알림과 같음), 여러 건: "새 제보 3건". */
    static String title(List<ReportDigestCandidate> reports) {
        if (reports.size() == 1) {
            ReportDigestCandidate only = reports.get(0);
            return ReportPushDispatcher.newReportTitle(ReportPushDispatcher.place(only.buildingName(), only.floor()));
        }
        return "새 제보 " + reports.size() + "건";
    }

    /** 1건: 제보 제목 그대로, 여러 건: "가장 최근 제목 외 2건"(제목이 길면 40자에서 말줄임). */
    static String body(List<ReportDigestCandidate> reports) {
        String latest = reports.get(0).title();
        if (reports.size() == 1) {
            return latest;
        }
        return shorten(latest) + " 외 " + (reports.size() - 1) + "건";
    }

    static String shorten(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.codePointCount(0, trimmed.length()) <= MAX_LEAD_TITLE_LENGTH) {
            return trimmed;
        }
        int end = trimmed.offsetByCodePoints(0, MAX_LEAD_TITLE_LENGTH - 1);
        return trimmed.substring(0, end).stripTrailing() + "…";
    }
}
