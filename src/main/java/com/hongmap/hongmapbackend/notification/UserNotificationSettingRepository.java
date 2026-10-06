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
     * 관리자 알림을 켠 관리자는 뺀다 — 같은 제보를 등록 때 "[관리] 새 제보 승인 대기"로 이미 받았고 직접 승인까지 해,
     * 승인 뒤 "새 제보" 알림이 한 번 더 오면 중복이다(10-05 제보). 관리자 알림을 끈 관리자는 일반 사용자처럼 받는다.
     * 조회 후 갱신이 아니라 조건부 UPDATE 한 번이라, 승인이 동시에 두 건 들어와도 같은 유저를 두 번 고르지 않는다
     * (두 번째 UPDATE는 행 잠금을 기다린 뒤 조건을 다시 보고 건너뛴다). 선점한 유저는 last_sent_at = now로 찾는다.
     * 같은 제보로 이미 제보 키워드 알림을 받은 유저(report_keyword_push_log)도 뺀다 — 한 제보로 두 번 받지 않게.
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
              AND (s.adminAlertsEnabled = false OR s.userId NOT IN (
                    SELECT u.id FROM User u WHERE u.role = com.hongmap.hongmapbackend.user.UserRole.ADMIN))
              AND s.userId NOT IN (
                    SELECT l.userId FROM ReportKeywordPushLog l WHERE l.reportId = :reportId)
            """)
    int claimNewReportRecipients(
            @Param("scope") NewReportScope scope,
            @Param("reportId") Long reportId,
            @Param("authorId") Long authorId,
            @Param("now") LocalDateTime now,
            @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * 제보 키워드 알림을 보낸 유저의 new_report_last_sent_at 을 sentAt 으로 바꾼다 — 키워드 알림 직후 일반 새 제보 알림이
     * 연달아 오지 않게(빈도 제한에 포함). 키워드 알림 자체는 빈도 제한을 받지 않는다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserNotificationSetting s SET s.newReportLastSentAt = :sentAt WHERE s.userId IN :userIds")
    int markNewReportSent(@Param("userIds") java.util.Collection<Long> userIds, @Param("sentAt") LocalDateTime sentAt);

    void deleteByUserId(Long userId);
}
