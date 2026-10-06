package com.hongmap.hongmapbackend.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReportCommentRepository extends JpaRepository<ReportComment, Long> {

    /**
     * 공개 목록의 최상위 댓글. 공개(VISIBLE) 댓글과, 지워졌거나 숨겨졌지만 공개 답글이 남은 댓글(자리 표시 — "삭제된 댓글이에요")을
     * 함께 준다. 작성자를 함께 읽어 N+1 을 막는다. 정렬은 Pageable(id ASC/DESC).
     */
    @EntityGraph(attributePaths = "user")
    @Query(value = """
            SELECT c FROM ReportComment c
            WHERE c.report.id = :reportId AND c.parent IS NULL
              AND (c.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE
                   OR EXISTS (SELECT 1 FROM ReportComment r WHERE r.parent = c
                              AND r.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE))
            """,
            countQuery = """
            SELECT COUNT(c) FROM ReportComment c
            WHERE c.report.id = :reportId AND c.parent IS NULL
              AND (c.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE
                   OR EXISTS (SELECT 1 FROM ReportComment r WHERE r.parent = c
                              AND r.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE))
            """)
    Page<ReportComment> findThreadRoots(@Param("reportId") Long reportId, Pageable pageable);

    /** findThreadRoots 와 같은 대상을 👍 많은 순(같으면 최신 순)으로. */
    @EntityGraph(attributePaths = "user")
    @Query(value = """
            SELECT c FROM ReportComment c
            WHERE c.report.id = :reportId AND c.parent IS NULL
              AND (c.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE
                   OR EXISTS (SELECT 1 FROM ReportComment r WHERE r.parent = c
                              AND r.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE))
            ORDER BY (SELECT COUNT(l) FROM ReportCommentLike l WHERE l.comment = c) DESC, c.id DESC
            """,
            countQuery = """
            SELECT COUNT(c) FROM ReportComment c
            WHERE c.report.id = :reportId AND c.parent IS NULL
              AND (c.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE
                   OR EXISTS (SELECT 1 FROM ReportComment r WHERE r.parent = c
                              AND r.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE))
            """)
    Page<ReportComment> findThreadRootsByLikes(@Param("reportId") Long reportId, Pageable pageable);

    /** 여러 최상위 댓글의 공개 답글 전부(오래된 순, 작성자 함께). 목록 한 페이지에 쿼리 한 번. */
    @Query("""
            SELECT c FROM ReportComment c JOIN FETCH c.user
            WHERE c.parent.id IN :parentIds
              AND c.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE
            ORDER BY c.id ASC
            """)
    List<ReportComment> findVisibleRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds);

    /** 한 댓글의 공개 답글(오래된 순, 페이지). */
    @EntityGraph(attributePaths = "user")
    Page<ReportComment> findByParent_IdAndStatus(Long parentId, ReportCommentStatus status, Pageable pageable);

    long countByReport_IdAndStatus(Long reportId, ReportCommentStatus status);

    boolean existsByParent_IdAndStatus(Long parentId, ReportCommentStatus status);

    /** 관리자 목록 — 상태 무관, 오래된 순. */
    @Query("SELECT c FROM ReportComment c JOIN FETCH c.user WHERE c.report.id = :reportId ORDER BY c.id ASC")
    List<ReportComment> findAllForAdmin(@Param("reportId") Long reportId);

    /** 지도 목록의 commentCount 일괄 조회(제보마다 따로 세지 않음). 행: [reportId(Long), count(Long)] */
    @Query("""
            SELECT c.report.id, COUNT(c) FROM ReportComment c
            WHERE c.report.id IN :reportIds
              AND c.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE
            GROUP BY c.report.id
            """)
    List<Object[]> countVisibleByReportIds(@Param("reportIds") Collection<Long> reportIds);

    /** 작성 빈도 제한용 — 상태와 상관없이 센다(지우고 다시 쓰기로 제한을 피하지 못하게). */
    long countByUser_IdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** 신고 누적 자동 숨김. 조건부 UPDATE 라 동시 신고에도 한 번만 1 을 돌려준다. */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ReportComment c SET c.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.HIDDEN
            WHERE c.id = :id AND c.status = com.hongmap.hongmapbackend.comment.ReportCommentStatus.VISIBLE
            """)
    int hideIfVisible(@Param("id") Long id);

    /**
     * 검토할 신고된 댓글: 마지막 관리자 검토(reviewedAt) 뒤 신고가 1건 이상인 댓글(공개 중이거나 자동 숨김된 것).
     * 작성자가 지운(DELETED) 댓글은 더 볼 것이 없어 뺀다. 마지막 신고가 최근인 순. 작성자·제보를 함께 읽어 N+1 을 막는다.
     */
    @Query("""
            SELECT c FROM ReportComment c JOIN FETCH c.user JOIN FETCH c.report
            WHERE c.status <> com.hongmap.hongmapbackend.comment.ReportCommentStatus.DELETED
              AND EXISTS (SELECT 1 FROM ReportCommentFlag f WHERE f.comment = c
                          AND (c.reviewedAt IS NULL OR f.createdAt > c.reviewedAt))
            ORDER BY (SELECT MAX(f2.createdAt) FROM ReportCommentFlag f2 WHERE f2.comment = c) DESC, c.id DESC
            """)
    List<ReportComment> findFlaggedPending(Pageable pageable);

    /** findFlaggedPending 과 같은 조건의 수(관리자 대시보드 comments.flaggedPending). */
    @Query("""
            SELECT COUNT(c) FROM ReportComment c
            WHERE c.status <> com.hongmap.hongmapbackend.comment.ReportCommentStatus.DELETED
              AND EXISTS (SELECT 1 FROM ReportCommentFlag f WHERE f.comment = c
                          AND (c.reviewedAt IS NULL OR f.createdAt > c.reviewedAt))
            """)
    long countFlaggedPending();
}
