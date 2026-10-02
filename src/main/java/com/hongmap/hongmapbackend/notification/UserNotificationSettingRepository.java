package com.hongmap.hongmapbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface UserNotificationSettingRepository extends JpaRepository<UserNotificationSetting, Long> {

    /**
     * 새 제보 푸시를 받을 유저를 "선점"한다: 새 제보 알림을 켰고(범위 일치), 작성자가 아니며,
     * 마지막 발송이 cutoff 이전(또는 없음)인 유저의 new_report_last_sent_at을 now로 바꾼다.
     * 조회 후 갱신이 아니라 조건부 UPDATE 한 번이라, 승인이 동시에 두 건 들어와도 같은 유저를 두 번 고르지 않는다
     * (두 번째 UPDATE는 행 잠금을 기다린 뒤 조건을 다시 보고 건너뛴다). 선점한 유저는 last_sent_at = now로 찾는다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE UserNotificationSetting s
            SET s.newReportLastSentAt = :now
            WHERE s.newReportsEnabled = true
              AND s.newReportsScope = :scope
              AND s.userId <> :authorId
              AND (s.newReportLastSentAt IS NULL OR s.newReportLastSentAt < :cutoff)
            """)
    int claimNewReportRecipients(
            @Param("scope") NewReportScope scope,
            @Param("authorId") Long authorId,
            @Param("now") LocalDateTime now,
            @Param("cutoff") LocalDateTime cutoff
    );

    void deleteByUserId(Long userId);
}
