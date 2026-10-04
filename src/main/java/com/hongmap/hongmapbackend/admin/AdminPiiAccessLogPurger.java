package com.hongmap.hongmapbackend.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * admin_pii_access_logs 보관 기간 정리. 「개인정보의 안전성 확보조치 기준」 제8조는 접속기록을 1년 이상 보관하도록 하므로
 * 기본 2년(730일)을 두고, 설정을 그보다 짧게 잡아도 1년(365일) 아래로는 지우지 않는다. 매일 04:30(서버 시각) 한 번.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminPiiAccessLogPurger {

    /** 법정 최소 보관 기간(일). 설정값이 이보다 작으면 이 값으로 올린다. */
    static final int MIN_RETENTION_DAYS = 365;

    private final AdminPiiAccessLogRepository repository;

    @Value("${admin.pii-access-log.retention-days:730}")
    int retentionDays = 730;

    @Scheduled(cron = "${admin.pii-access-log.purge-cron:0 30 4 * * *}")
    @Transactional
    public void purge() {
        purgeOlderThan(LocalDateTime.now(), retentionDays);
    }

    /**
     * now 기준 retentionDays(최소 365일로 올림)가 지난 기록을 지우고 지운 행 수를 돌려준다.
     * 테스트가 시각·기간을 정해 직접 부른다(빈은 트랜잭션 프록시라 필드를 바꿔 넣을 수 없어서 인자로 받는다).
     */
    @Transactional
    public int purgeOlderThan(LocalDateTime now, int retentionDays) {
        int days = effectiveRetentionDays(retentionDays);
        int deleted = repository.deleteByAccessedAtBefore(now.minusDays(days));
        if (deleted > 0) {
            log.info("관리자 개인정보 열람 기록 {}건 삭제(보관 {}일 경과)", deleted, days);
        }
        return deleted;
    }

    static int effectiveRetentionDays(int retentionDays) {
        return Math.max(MIN_RETENTION_DAYS, retentionDays);
    }
}
