package com.hongmap.hongmapbackend.feedback;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /** status 가 null 이면 전부. 작성자를 함께 읽어 목록 N+1 을 막는다(비로그인 문의는 user 가 없어 LEFT JOIN). */
    @Query("""
            SELECT f FROM Feedback f
            LEFT JOIN FETCH f.user
            WHERE :status IS NULL OR f.status = :status
            ORDER BY f.createdAt DESC
            """)
    List<Feedback> findForAdmin(@Param("status") FeedbackStatus status, Pageable pageable);

    long countByStatus(FeedbackStatus status);
}
