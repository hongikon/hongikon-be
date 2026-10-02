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
}
