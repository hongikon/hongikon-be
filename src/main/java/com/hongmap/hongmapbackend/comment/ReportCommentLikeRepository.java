package com.hongmap.hongmapbackend.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReportCommentLikeRepository extends JpaRepository<ReportCommentLike, Long> {

    boolean existsByComment_IdAndUser_Id(Long commentId, Long userId);

    long countByComment_Id(Long commentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ReportCommentLike l WHERE l.comment.id = :commentId AND l.user.id = :userId")
    int deleteMine(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /** 행: [commentId(Long), count(Long)] */
    @Query("""
            SELECT l.comment.id, COUNT(l) FROM ReportCommentLike l
            WHERE l.comment.id IN :commentIds GROUP BY l.comment.id
            """)
    List<Object[]> countByCommentIds(@Param("commentIds") Collection<Long> commentIds);

    @Query("SELECT l.comment.id FROM ReportCommentLike l WHERE l.comment.id IN :commentIds AND l.user.id = :userId")
    List<Long> findLikedCommentIds(@Param("commentIds") Collection<Long> commentIds, @Param("userId") Long userId);
}
