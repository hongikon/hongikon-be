package com.hongmap.hongmapbackend.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportFlagRepository extends JpaRepository<ReportFlag, Long> {

    boolean existsByReportIdAndUserId(Long reportId, Long userId);

    long countByReportId(Long reportId);

    void deleteByUser_Id(Long userId);

    void deleteByReport_User_Id(Long userId);
}
