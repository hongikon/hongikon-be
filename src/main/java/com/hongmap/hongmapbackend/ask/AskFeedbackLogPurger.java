package com.hongmap.hongmapbackend.ask;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * ask_feedback_logs(앱 안내 흐름 답의 '해결됐나요' 응답) 보관 기간 정리(11-06). 처리방침 '물어보기 대화 기록은 1년 뒤 자동 삭제'와
 * 같은 기간 — 서버 답 기록(AskConversationRecorder.purgeExpired)과 같은 시각에 돈다.
 */
@Slf4j
@Component
public class AskFeedbackLogPurger {

    private final AskFeedbackLogRepository repository;
    private final int retentionDays;
    private final Clock clock = Clock.systemUTC();

    public AskFeedbackLogPurger(AskFeedbackLogRepository repository,
                                @Value("${ask.log.retention-days:365}") int retentionDays) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${ask.log.purge-cron:0 50 3 * * *}")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteCreatedBefore(LocalDateTime.now(clock).minusDays(retentionDays));
        if (deleted > 0) {
            log.info("보관 기간이 지난 물어보기 응답 기록 {}건 삭제", deleted);
        }
    }
}
