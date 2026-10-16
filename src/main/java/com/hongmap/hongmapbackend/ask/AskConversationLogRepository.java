package com.hongmap.hongmapbackend.ask;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AskConversationLogRepository extends JpaRepository<AskConversationLog, Long> {

    @Modifying
    @Query("DELETE FROM AskConversationLog l WHERE l.createdAt < :before")
    int deleteCreatedBefore(@Param("before") LocalDateTime before);
}
