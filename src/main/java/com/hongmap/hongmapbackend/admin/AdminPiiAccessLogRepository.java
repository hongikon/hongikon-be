package com.hongmap.hongmapbackend.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminPiiAccessLogRepository extends JpaRepository<AdminPiiAccessLog, Long> {

    List<AdminPiiAccessLog> findByTargetUserIdOrderByIdDesc(Long targetUserId);

    /** 보관 기간이 지난 기록 일괄 삭제(엔티티를 읽지 않는 벌크 DELETE). 지운 행 수를 돌려준다. */
    @Modifying
    @Query("DELETE FROM AdminPiiAccessLog l WHERE l.accessedAt < :before")
    int deleteByAccessedAtBefore(@Param("before") LocalDateTime before);
}
