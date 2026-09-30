package com.hongmap.hongmapbackend.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReportFlagRepository extends JpaRepository<ReportFlag, Long> {

    boolean existsByReportIdAndUserId(Long reportId, Long userId);

    long countByReportId(Long reportId);

    void deleteByUser_Id(Long userId);

    void deleteByReport_User_Id(Long userId);

    @Query("SELECT f FROM ReportFlag f JOIN FETCH f.user WHERE f.report.id = :reportId ORDER BY f.createdAt DESC")
    List<ReportFlag> findWithUserByReportId(@Param("reportId") Long reportId);

    /** 관리자 목록용 신고 수 일괄 조회. 행: [reportId(Long), count(Long)] */
    @Query("SELECT f.report.id, COUNT(f) FROM ReportFlag f WHERE f.report.id IN :reportIds GROUP BY f.report.id")
    List<Object[]> countByReportIds(@Param("reportIds") Collection<Long> reportIds);
}
