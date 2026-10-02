package com.hongmap.hongmapbackend.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReportReactionRepository extends JpaRepository<ReportReaction, Long> {

    boolean existsByReport_IdAndUser_Id(Long reportId, Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ReportReaction r WHERE r.report.id = :reportId AND r.user.id = :userId")
    int deleteMine(@Param("reportId") Long reportId, @Param("userId") Long userId);

    long countByReport_Id(Long reportId);

    long countByReport_IdAndCreatedAtGreaterThanEqual(Long reportId, LocalDateTime since);

    /**
     * 지도 목록에 붙일 커뮤니티 값을 제보 여러 개에 대해 쿼리 한 번으로 읽는다.
     * 행: [reportId, 🔥 수, 최근 🔥 수(since 이후), 내가 🔥(0/1), 내가 관심(0/1), 조회 수(null 가능), 작성자 알림(null 가능)].
     * userId 가 없으면(게스트) -1 을 넘긴다.
     */
    @Query(nativeQuery = true, value = """
            SELECT r.id,
                   (SELECT COUNT(*) FROM report_reactions x WHERE x.report_id = r.id),
                   (SELECT COUNT(*) FROM report_reactions x WHERE x.report_id = r.id AND x.created_at >= :since),
                   (SELECT COUNT(*) FROM report_reactions x WHERE x.report_id = r.id AND x.user_id = :userId),
                   (SELECT COUNT(*) FROM report_follows f WHERE f.report_id = r.id AND f.user_id = :userId),
                   e.view_count,
                   e.author_notify_enabled
            FROM reports r
            LEFT JOIN report_engagement e ON e.report_id = r.id
            WHERE r.id IN (:reportIds)
            """)
    List<Object[]> findStats(@Param("reportIds") Collection<Long> reportIds,
                             @Param("since") LocalDateTime since,
                             @Param("userId") Long userId);
}
