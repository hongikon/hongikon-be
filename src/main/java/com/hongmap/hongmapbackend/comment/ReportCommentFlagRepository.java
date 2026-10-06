package com.hongmap.hongmapbackend.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReportCommentFlagRepository extends JpaRepository<ReportCommentFlag, Long> {

    boolean existsByComment_IdAndUser_Id(Long commentId, Long userId);

    long countByComment_Id(Long commentId);

    long countByComment_IdAndCreatedAtAfter(Long commentId, LocalDateTime since);

    /** 관리자 목록용 사유별 신고 수. 행: [commentId(Long), reason(String), count(Long)] */
    @Query("""
            SELECT f.comment.id, f.reason, COUNT(f) FROM ReportCommentFlag f
            WHERE f.comment.id IN :commentIds
            GROUP BY f.comment.id, f.reason
            """)
    List<Object[]> countByCommentIdsAndReason(@Param("commentIds") Collection<Long> commentIds);

    /** 이 사람이 신고한 댓글 id(목록의 flaggedByMe — 페이지마다 쿼리 한 번). */
    @Query("SELECT f.comment.id FROM ReportCommentFlag f WHERE f.comment.id IN :commentIds AND f.user.id = :userId")
    List<Long> findFlaggedCommentIds(@Param("commentIds") Collection<Long> commentIds, @Param("userId") Long userId);

    /**
     * 마지막 관리자 검토(reviewedAt) 뒤 들어온 신고 수와 마지막 신고 시각. "신고된 댓글" 목록용.
     * 행: [commentId(Long), count(Long), lastFlaggedAt(LocalDateTime)]
     */
    @Query("""
            SELECT f.comment.id, COUNT(f), MAX(f.createdAt) FROM ReportCommentFlag f
            WHERE f.comment.id IN :commentIds
              AND (f.comment.reviewedAt IS NULL OR f.createdAt > f.comment.reviewedAt)
            GROUP BY f.comment.id
            """)
    List<Object[]> countPendingByCommentIds(@Param("commentIds") Collection<Long> commentIds);
}
