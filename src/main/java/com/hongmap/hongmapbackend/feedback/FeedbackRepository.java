package com.hongmap.hongmapbackend.feedback;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 회원탈퇴 시 문의 내용만 남기고 작성자와 답변용 연락처(contact, 대개 이메일)를 비운다.
     * 처리방침 "작성자와의 연결을 끊은 상태로 내용만 남을 수 있으며"와 맞춘다.
     * 운영 DB FK는 ON DELETE SET NULL(db/alter_admin_console.sql)이지만 그것만으로는 contact가 남으므로
     * 반드시 users 삭제 전에 이 쿼리로 함께 지운다.
     */
    /** 탈퇴하는 회원이 문의에 붙인 사진 키들(쉼표로 이은 값, 문의마다 한 줄). 탈퇴 때 S3 에서 지운다. */
    @Query("SELECT f.imageKeys FROM Feedback f WHERE f.user.id = :userId AND f.imageKeys IS NOT NULL")
    java.util.List<String> findImageKeysByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Feedback f SET f.user = null, f.contact = null, f.imageKeys = null WHERE f.user.id = :userId")
    int detachUser(@Param("userId") Long userId);
}
