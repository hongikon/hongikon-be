package com.hongmap.hongmapbackend.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/**
 * report_engagement·report_view_marks 쓰기. 동시 요청에도 맞게 행 단위 조건부 UPDATE·INSERT IGNORE 만 쓴다
 * (MySQL, H2 MySQL 모드 모두 지원).
 */
public interface ReportEngagementRepository extends JpaRepository<ReportEngagement, Long> {

    /** 제보 상태 행이 없으면 기본값으로 만든다(있으면 그대로). */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT IGNORE INTO report_engagement (report_id, view_count, author_notify_enabled, fire_milestone_sent, updated_at)
            VALUES (:reportId, 0, TRUE, 0, CURRENT_TIMESTAMP)
            """)
    int ensureRow(@Param("reportId") Long reportId);

    /** 오늘 처음 본 사람이면 1(표식 저장), 이미 봤으면 0. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT IGNORE INTO report_view_marks (report_id, viewer_key, view_date)
            VALUES (:reportId, :viewerKey, :viewDate)
            """)
    int insertViewMark(@Param("reportId") Long reportId, @Param("viewerKey") String viewerKey,
                       @Param("viewDate") LocalDate viewDate);

    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE report_engagement SET view_count = view_count + 1, updated_at = CURRENT_TIMESTAMP
            WHERE report_id = :reportId
            """)
    int incrementViews(@Param("reportId") Long reportId);

    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE report_engagement SET author_notify_enabled = :enabled, updated_at = CURRENT_TIMESTAMP
            WHERE report_id = :reportId
            """)
    int updateAuthorNotify(@Param("reportId") Long reportId, @Param("enabled") boolean enabled);

    /** 이정표를 처음 넘긴 요청만 1 — 동시에 10번째 🔥가 두 번 와도 알림은 한 번. */
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE report_engagement SET fire_milestone_sent = :milestone, updated_at = CURRENT_TIMESTAMP
            WHERE report_id = :reportId AND fire_milestone_sent < :milestone
            """)
    int claimFireMilestone(@Param("reportId") Long reportId, @Param("milestone") int milestone);

    @Modifying
    @Query("DELETE FROM ReportViewMark m WHERE m.viewDate < :cutoff")
    int deleteViewMarksBefore(@Param("cutoff") LocalDate cutoff);
}
