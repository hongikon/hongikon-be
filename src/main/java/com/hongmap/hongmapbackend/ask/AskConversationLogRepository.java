package com.hongmap.hongmapbackend.ask;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AskConversationLogRepository extends JpaRepository<AskConversationLog, Long> {

    @Modifying
    @Query("DELETE FROM AskConversationLog l WHERE l.createdAt < :before")
    int deleteCreatedBefore(@Param("before") LocalDateTime before);

    /** 관리자 '물어보기 피드백': since 뒤의 '해결 안 됨' 행, 최신순. reviewed=null 이면 확인 여부 상관없이. */
    @Query("""
            SELECT l FROM AskConversationLog l
            WHERE l.resolved = false AND l.createdAt >= :since
              AND (:reviewed IS NULL OR (:reviewed = true AND l.reviewedAt IS NOT NULL) OR (:reviewed = false AND l.reviewedAt IS NULL))
            ORDER BY l.createdAt DESC
            """)
    List<AskConversationLog> findUnresolved(@Param("since") LocalDateTime since, @Param("reviewed") Boolean reviewed, Pageable pageable);

    /** '요즘 많이 찾는 안내'(AskPopularGuides): since 뒤 기록의 (대화 id, 안내 id들). '해결 안 됨'은 뺀다. */
    @Query("""
            SELECT l.conversationId, l.nodeIds FROM AskConversationLog l
            WHERE l.createdAt >= :since AND l.outcome IN :outcomes AND (l.resolved IS NULL OR l.resolved = true)
            """)
    List<Object[]> findGuideUses(@Param("since") LocalDateTime since, @Param("outcomes") Collection<AskConversationLog.Outcome> outcomes);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    long countByResolvedAndCreatedAtGreaterThanEqual(Boolean resolved, LocalDateTime since);
}
