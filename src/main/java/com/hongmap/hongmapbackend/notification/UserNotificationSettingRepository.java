package com.hongmap.hongmapbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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
    int markNewReportSent(@Param("userIds") Collection<Long> userIds, @Param("sentAt") LocalDateTime sentAt);

    /**
     * 새 제보 다이제스트 후보 유저(NewReportDigestScheduler) — 일반 새 제보 알림 대상과 같은 기준(새 제보 알림 켬, 범위 일치,
     * 관리자 알림을 켠 관리자 제외)에 빈도 제한이 풀렸고(cutoff 이전·없음) 활성 기기가 있는 유저. 유저별 다이제스트 구간 계산용
     * 마지막 발송 시각·설정 변경 시각을 같이 읽는다.
     */
    @Query("""
            SELECT new com.hongmap.hongmapbackend.notification.NewReportDigestRecipient(
                s.userId, s.newReportLastSentAt, s.updatedAt)
            FROM UserNotificationSetting s
            WHERE s.newReportsEnabled = true
              AND s.newReportsScope = :scope
              AND (s.newReportLastSentAt IS NULL OR s.newReportLastSentAt < :cutoff)
              AND (s.adminAlertsEnabled = false OR s.userId NOT IN (
                    SELECT u.id FROM User u WHERE u.role = com.hongmap.hongmapbackend.user.UserRole.ADMIN))
              AND s.userId IN (
                    SELECT d.user.id FROM UserDevice d WHERE d.active = true AND d.tokenType = :tokenType)
            ORDER BY s.userId ASC
            """)
    List<NewReportDigestRecipient> findDigestRecipients(
            @Param("scope") NewReportScope scope,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("tokenType") com.hongmap.hongmapbackend.user.TokenType tokenType
    );

    /**
     * 다이제스트를 보낼 유저를 선점한다 — claimNewReportRecipients 와 같은 조건부 UPDATE(아직 빈도 제한이 풀려 있고 범위가 맞을 때만
     * now 로). 서버 여러 대·즉시 알림과 동시에 돌아도 같은 유저를 두 번 고르지 않는다. 선점한 유저는 last_sent_at = now 로 찾는다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE UserNotificationSetting s
            SET s.newReportLastSentAt = :now
            WHERE s.userId IN :userIds
              AND s.newReportsEnabled = true
              AND s.newReportsScope = :scope
              AND (s.newReportLastSentAt IS NULL OR s.newReportLastSentAt < :cutoff)
            """)
    int claimDigestRecipients(
            @Param("userIds") Collection<Long> userIds,
            @Param("scope") NewReportScope scope,
            @Param("now") LocalDateTime now,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Query("SELECT s.userId FROM UserNotificationSetting s WHERE s.userId IN :userIds AND s.newReportLastSentAt = :claimedAt")
    List<Long> findUserIdsClaimedAt(@Param("userIds") Collection<Long> userIds, @Param("claimedAt") LocalDateTime claimedAt);

    void deleteByUserId(Long userId);
}
