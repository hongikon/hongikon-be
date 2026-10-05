package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.user.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReportFollowRepository extends JpaRepository<ReportFollow, Long> {

    boolean existsByReport_IdAndUser_Id(Long reportId, Long userId);

    long countByUser_Id(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ReportFollow f WHERE f.report.id = :reportId AND f.user.id = :userId")
    int deleteMine(@Param("reportId") Long reportId, @Param("userId") Long userId);

    @Query("SELECT f.user.id FROM ReportFollow f WHERE f.report.id = :reportId")
    List<Long> findFollowerIds(@Param("reportId") Long reportId);

    /** 방금 시작한(from < startsAt <= now) 공개 제보의, 시작 전에 관심 등록했고 아직 시작 알림을 받지 않은 관심. */
    @Query("""
            SELECT f FROM ReportFollow f JOIN FETCH f.report r
            WHERE f.startNotified = false
              AND r.status = com.hongmap.hongmapbackend.report.ReportStatus.ACTIVE
              AND r.startsAt <= :now AND r.startsAt > :from
              AND f.createdAt < r.startsAt
            """)
    List<ReportFollow> findStartCandidates(@Param("now") LocalDateTime now, @Param("from") LocalDateTime from);

    /** 곧 끝나는(now < endsAt <= until) 공개 제보의, 아직 "곧 끝나요" 알림을 받지 않은 관심. */
    @Query("""
            SELECT f FROM ReportFollow f JOIN FETCH f.report r
            WHERE f.endingNotified = false
              AND r.status = com.hongmap.hongmapbackend.report.ReportStatus.ACTIVE
              AND r.endsAt > :now AND r.endsAt <= :until
            """)
    List<ReportFollow> findEndingCandidates(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    /** 끝났거나 공개가 내려간(숨김·반려·삭제) 제보의 관심을 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM ReportFollow f WHERE f.report.id IN (
                SELECT r.id FROM Report r
                WHERE r.endsAt < :now OR r.status <> com.hongmap.hongmapbackend.report.ReportStatus.ACTIVE)
            """)
    int deleteFinished(@Param("now") LocalDateTime now);

    /**
     * 제보 알림을 받을 수 있는 사용자들의 활성 Expo 기기. "내 제보 결과 알림"(report_status_enabled)을 끈 사람은 뺀다
     * (행이 없으면 켜짐).
     */
    @Query("""
            SELECT d FROM UserDevice d
            WHERE d.active = true
              AND d.tokenType = com.hongmap.hongmapbackend.user.TokenType.EXPO
              AND d.user.id IN :userIds
              AND d.user.id NOT IN (
                  SELECT s.userId FROM UserNotificationSetting s WHERE s.reportStatusEnabled = false)
            """)
    List<UserDevice> findAlertDevices(@Param("userIds") Collection<Long> userIds);
}
