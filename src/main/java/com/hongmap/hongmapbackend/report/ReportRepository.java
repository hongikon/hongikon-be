package com.hongmap.hongmapbackend.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("""
            SELECT r FROM Report r
            WHERE r.status = 'ACTIVE'
              AND :now BETWEEN r.startsAt AND r.endsAt
              AND (:buildingId IS NULL OR r.building.id = :buildingId)
            ORDER BY r.createdAt DESC
            """)
    List<Report> findLiveReports(@Param("now") LocalDateTime now, @Param("buildingId") Long buildingId);

    @Query("""
            SELECT r FROM Report r
            WHERE (:buildingId IS NULL OR r.building.id = :buildingId)
            ORDER BY r.createdAt DESC
            """)
    List<Report> findAllByBuildingIdOptional(@Param("buildingId") Long buildingId);

    @Modifying
    @Query("UPDATE Report r SET r.status = :status WHERE r.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    void deleteByUser_Id(Long userId);
}
