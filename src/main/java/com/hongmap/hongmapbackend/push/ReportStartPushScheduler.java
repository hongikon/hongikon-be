package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportModeratedEvent;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 예정 제보의 캠퍼스 새 제보 알림을 시작 시각에 보낸다.
 *
 * 시작 전에 승인된 제보는 승인 때 새 제보 알림을 보내지 않는다(ReportPushDispatcher) — 알림을 눌렀을 때 지도에 아직 없기 때문이다.
 * 그래서 1분마다 "직전 확인 시각 ~ 지금" 사이에 시작한 그런 제보를 찾아 보낸다. 상태 컬럼 없이 확인 구간만 메모리에 둔다:
 * <ul>
 *   <li>서버가 막 떴을 때는 {@link #STARTUP_LOOKBACK}만큼 거슬러 본다(배포 중 시작한 제보를 놓치지 않게).</li>
 *   <li>그 때문에 같은 제보를 두 번 집을 수 있지만, 보내기 전에 reports.published_at 을 비어 있을 때만 채워(조건부 UPDATE)
 *       먼저 채운 쪽만 보낸다 — 같은 제보로 두 번 보내지 않는다(서버 여러 대여도). 제보 키워드 알림은 발송 기록
 *       (report_keyword_push_log)도 따로 중복을 막는다.</li>
 *   <li>빈도 제한·방해 금지 시간에 걸려 일반 알림을 못 받은 유저에게는 NewReportDigestScheduler 가 나중에 모아 보낸다.</li>
 *   <li>서버가 10분 넘게 꺼져 있던 사이 시작한 제보는 알림 없이 지도에만 뜬다(알림은 보조 수단이라 감수).</li>
 * </ul>
 * 서버 1대 기준이다. 여러 대로 늘리면 구간을 DB에 두거나 한 대에서만 돌린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportStartPushScheduler {

    static final Duration STARTUP_LOOKBACK = Duration.ofMinutes(10);

    private final ReportRepository reportRepository;
    private final ReportPushDispatcher reportPushDispatcher;

    /** 마지막으로 확인한 구간의 끝. null 이면 아직 한 번도 안 돌았다. */
    private LocalDateTime checkedUntil;

    @Scheduled(cron = "${push.report-start-cron:30 * * * * *}")
    public void run() {
        try {
            runUntil(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("예정 제보 시작 알림 확인 실패: {}", e.getMessage());
        }
    }

    /** (직전 확인 시각, now] 에 시작한 예정 제보에 새 제보 알림을 보낸다. 보낸 제보 수를 돌려준다(테스트용). */
    synchronized int runUntil(LocalDateTime now) {
        LocalDateTime from = checkedUntil != null ? checkedUntil : now.minus(STARTUP_LOOKBACK);
        if (!now.isAfter(from)) {
            return 0;
        }
        List<Report> started = reportRepository.findStartedAfterEarlyApproval(ReportStatus.ACTIVE, from, now);
        checkedUntil = now;
        LocalDateTime publishedAt = now.truncatedTo(ChronoUnit.MICROS);
        for (Report report : started) {
            try {
                // 새 제보로 공개된 시각을 먼저 남긴 쪽만 보낸다 — 다시 집혀도(재시작 lookback·서버 여러 대) 한 번만 가고,
                // 이 시각이 새 제보 다이제스트(NewReportDigestScheduler)의 기준이 된다(시작 시각이 아니라 실제 알림 시각).
                if (reportRepository.markPublishedIfUnset(report.getId(), publishedAt) == 0) {
                    continue;
                }
                int accepted = reportPushDispatcher.dispatchStarted(new ReportModeratedEvent(
                        report.getId(), report.getUser().getId(), report.getTitle(),
                        report.getBuilding().getName(), report.getFloor(),
                        ReportStatus.ACTIVE, ReportStatus.ACTIVE, null, report.getEndsAt(), report.getStartsAt(),
                        report.getContent(), report.getPlaceLabel(), report.getCustomCategoryLabel()));
                log.info("예정 제보 시작 알림: reportId={}, {}건 접수", report.getId(), accepted);
            } catch (Exception e) {
                log.warn("예정 제보 시작 알림 실패 (reportId={}): {}", report.getId(), e.getMessage());
            }
        }
        return started.size();
    }

    /** 테스트에서 확인 구간을 정한다. */
    synchronized void resetCheckedUntil(LocalDateTime value) {
        checkedUntil = value;
    }
}
