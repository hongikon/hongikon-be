package com.hongmap.hongmapbackend.ask;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AskFeedbackLogRepository extends JpaRepository<AskFeedbackLog, Long> {

    Optional<AskFeedbackLog> findFirstByClientIdOrderByIdDesc(String clientId);

    /** 관리자 '물어보기 피드백': since 뒤의 '해결 안 됨' 행, 최신순. reviewed=null 이면 확인 여부 상관없이. */
    @Query("""
            SELECT l FROM AskFeedbackLog l
            WHERE l.resolved = false AND l.createdAt >= :since
              AND (:reviewed IS NULL OR (:reviewed = true AND l.reviewedAt IS NOT NULL) OR (:reviewed = false AND l.reviewedAt IS NULL))
            ORDER BY l.createdAt DESC
            """)
    List<AskFeedbackLog> findUnresolved(@Param("since") LocalDateTime since, @Param("reviewed") Boolean reviewed, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AskFeedbackLog l WHERE l.createdAt < :before")
    int deleteCreatedBefore(@Param("before") LocalDateTime before);
}
