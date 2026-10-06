package com.hongmap.hongmapbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportKeywordPushLogRepository extends JpaRepository<ReportKeywordPushLog, Long> {

    boolean existsByReportIdAndUserId(Long reportId, Long userId);

    @Modifying
    @Query("DELETE FROM ReportKeywordPushLog l WHERE l.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
