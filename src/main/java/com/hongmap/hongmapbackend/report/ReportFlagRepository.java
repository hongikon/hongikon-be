package com.hongmap.hongmapbackend.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportFlagRepository extends JpaRepository<ReportFlag, Long> {

    boolean existsByReportIdAndUserId(Long reportId, Long userId);

    long countByReportId(Long reportId);
}
