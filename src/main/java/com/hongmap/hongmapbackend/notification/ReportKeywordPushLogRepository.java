package com.hongmap.hongmapbackend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReportKeywordPushLogRepository extends JpaRepository<ReportKeywordPushLog, Long> {

    boolean existsByReportIdAndUserId(Long reportId, Long userId);

    /** 이 제보들로 키워드 알림을 받은 [reportId, userId] 쌍 — 새 제보 다이제스트에서 이미 받은 제보를 빼는 데 쓴다. */
    @Query("SELECT l.reportId, l.userId FROM ReportKeywordPushLog l WHERE l.reportId IN :reportIds")
    List<Object[]> findPairsByReportIds(@Param("reportIds") Collection<Long> reportIds);

    @Modifying
    @Query("DELETE FROM ReportKeywordPushLog l WHERE l.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
