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

    /**
     * since 뒤에 들어온 신고가 있는지(since 가 null 이면 신고가 하나라도 있는지). 작성자 삭제 잠금(ReportService.delete)용 —
     * 숨김(HIDDEN) 뒤 운영진이 아직 보지 않은 신고가 남아 있으면 검토 전으로 본다.
     */
    @Query("SELECT COUNT(f) > 0 FROM ReportFlag f WHERE f.report.id = :reportId AND (:since IS NULL OR f.createdAt > :since)")
    boolean existsByReportIdCreatedAfter(@Param("reportId") Long reportId, @Param("since") java.time.LocalDateTime since);

    /** 작성자가 제보를 지울 때 그 제보의 신고도 지운다(운영 DB 는 FK ON DELETE CASCADE 지만 DB 에 기대지 않고 명시). */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ReportFlag f WHERE f.report.id = :reportId")
    int deleteAllByReportId(@Param("reportId") Long reportId);
}
