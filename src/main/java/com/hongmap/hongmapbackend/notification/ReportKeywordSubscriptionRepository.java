package com.hongmap.hongmapbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportKeywordSubscriptionRepository extends JpaRepository<ReportKeywordSubscription, Long> {

    /** 등록한 순서(id) — 여러 키워드가 걸리면 이 순서의 첫 키워드를 알림 제목에 쓴다. */
    List<ReportKeywordSubscription> findByUser_IdOrderByIdAsc(Long userId);

    Optional<ReportKeywordSubscription> findByIdAndUser_Id(Long id, Long userId);

    /** 대소문자 무시 중복 확인 — 운영 콜레이션(ci)과 같은 기준을 H2 에서도 쓰려고 명시한다. */
    boolean existsByUser_IdAndKeywordIgnoreCase(Long userId, String keyword);

    long countByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);

    /**
     * 새 제보 하나의 키워드 알림 후보 — 새 제보 알림을 켠(범위 무관) 활성 회원의 제보 키워드 전부를 한 번에 읽는다.
     * 작성자, 정지된 회원, 이 제보로 이미 키워드 알림을 받은 유저(report_keyword_push_log)는 뺀다.
     * 관리자 알림을 켠 관리자도 뺀다 — 캠퍼스 새 제보 알림(claimNewReportRecipients)과 같은 이유(승인 대기 알림으로 이미 받음).
     * 실제 매칭(공백·대소문자 무시 포함 여부)은 ReportPushDispatcher 가 자바에서 한다. 유저·등록 순서로 정렬.
     */
    @Query("""
            SELECT new com.hongmap.hongmapbackend.notification.ReportKeywordCandidate(k.user.id, k.keyword)
            FROM ReportKeywordSubscription k, UserNotificationSetting s
            WHERE s.userId = k.user.id
              AND s.newReportsEnabled = true
              AND k.user.id <> :authorId
              AND k.user.status = com.hongmap.hongmapbackend.user.UserStatus.ACTIVE
              AND (s.adminAlertsEnabled = false
                   OR k.user.role <> com.hongmap.hongmapbackend.user.UserRole.ADMIN)
              AND k.user.id NOT IN (
                  SELECT l.userId FROM ReportKeywordPushLog l WHERE l.reportId = :reportId)
            ORDER BY k.user.id ASC, k.id ASC
            """)
    List<ReportKeywordCandidate> findPushCandidates(@Param("reportId") Long reportId, @Param("authorId") Long authorId);
}
