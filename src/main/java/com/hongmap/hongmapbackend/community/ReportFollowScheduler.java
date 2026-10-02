package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.community.ReportCommunityPushDispatcher.FollowKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관심 제보 알림·정리(기본 매분 20초, push.report-follow-cron).
 *
 * <ol>
 *   <li>시작: 방금(10분 안) 시작한 공개 제보를 시작 전에 관심 등록한 사람에게 "관심 제보가 시작됐어요" 한 번.</li>
 *   <li>곧 끝남: push.report-follow-ending-minutes(30) 안에 끝나는 공개 제보를 그 전부터 관심 등록한 사람에게 "곧 끝나요" 한 번.</li>
 *   <li>정리: 끝났거나 공개가 내려간 제보의 관심을 지운다. 2일 지난 조회 중복 방지 표식(report_view_marks)도 지운다.</li>
 * </ol>
 * 보냈다는 표시는 트랜잭션 안에서 먼저 남기고 푸시는 커밋 뒤에 보낸다(실패해도 다시 보내지 않음 — 중복 알림보다 낫다).
 */
@Slf4j
@Component
public class ReportFollowScheduler {

    static final long START_GRACE_MINUTES = 10;

    private final ReportFollowRepository followRepository;
    private final ReportEngagementRepository engagementRepository;
    private final ReportCommunityPushDispatcher dispatcher;
    private final TransactionTemplate tx;
    private final long endingMinutes;

    public ReportFollowScheduler(ReportFollowRepository followRepository,
                                 ReportEngagementRepository engagementRepository,
                                 ReportCommunityPushDispatcher dispatcher,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${push.report-follow-ending-minutes:30}") long endingMinutes) {
        this.followRepository = followRepository;
        this.engagementRepository = engagementRepository;
        this.dispatcher = dispatcher;
        this.tx = new TransactionTemplate(transactionManager);
        this.endingMinutes = endingMinutes;
    }

    private record Alert(Long reportId, String title, List<Long> userIds, FollowKind kind) {
    }

    @Scheduled(cron = "${push.report-follow-cron:20 * * * * *}")
    public void scheduled() {
        try {
            run(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("관심 제보 스케줄 실패: {}", e.getMessage());
        }
    }

    /** 한 번 돈다. 보낸(Expo 접수) 메시지 수. 테스트가 시각을 넣어 부른다. */
    public int run(LocalDateTime now) {
        List<Alert> alerts = tx.execute(status -> {
            List<Alert> result = new ArrayList<>();
            Map<Long, Alert> starts = new LinkedHashMap<>();
            for (ReportFollow follow : followRepository.findStartCandidates(now, now.minusMinutes(START_GRACE_MINUTES))) {
                follow.markStartNotified();
                starts.computeIfAbsent(follow.getReport().getId(), id ->
                        new Alert(id, follow.getReport().getTitle(), new ArrayList<>(), FollowKind.START))
                        .userIds().add(follow.getUser().getId());
            }
            Map<Long, Alert> endings = new LinkedHashMap<>();
            for (ReportFollow follow : followRepository.findEndingCandidates(now, now.plusMinutes(endingMinutes))) {
                follow.markEndingNotified();
                // 곧 끝날 무렵에 막 관심 등록한 사람에게는 바로 "곧 끝나요"를 보내지 않는다.
                if (!follow.getCreatedAt().isBefore(follow.getReport().getEndsAt().minusMinutes(endingMinutes))) {
                    continue;
                }
                endings.computeIfAbsent(follow.getReport().getId(), id ->
                        new Alert(id, follow.getReport().getTitle(), new ArrayList<>(), FollowKind.ENDING))
                        .userIds().add(follow.getUser().getId());
            }
            int cleared = followRepository.deleteFinished(now);
            int marks = engagementRepository.deleteViewMarksBefore(
                    LocalDate.now(ReportCommunityService.KST).minusDays(1));
            if (cleared > 0 || marks > 0) {
                log.info("관심 제보 정리: 관심 {}건, 조회 표식 {}건", cleared, marks);
            }
            result.addAll(starts.values());
            result.addAll(endings.values());
            return result;
        });
        int accepted = 0;
        for (Alert alert : alerts == null ? List.<Alert>of() : alerts) {
            accepted += dispatcher.sendFollowAlert(alert.reportId(), alert.title(), alert.userIds(), alert.kind(),
                    endingMinutes);
        }
        return accepted;
    }
}
