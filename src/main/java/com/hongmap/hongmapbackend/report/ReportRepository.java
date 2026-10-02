package com.hongmap.hongmapbackend.report;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 지도용. 응답에 작성자 이름(displayName)을 싣기 때문에 작성자를 함께 읽어 작성자 수만큼의 추가 쿼리(N+1)를 막는다. */
    @Query("""
            SELECT r FROM Report r
            JOIN FETCH r.user
            WHERE r.status = :status
              AND :now BETWEEN r.startsAt AND r.endsAt
              AND (:buildingId IS NULL OR r.building.id = :buildingId)
            ORDER BY r.createdAt DESC
            """)
    List<Report> findLiveReports(@Param("status") ReportStatus status, @Param("now") LocalDateTime now,
                                  @Param("buildingId") Long buildingId);

    /** 아직 시작 전이고 to 안에 시작할 제보(예정). 시작 시각이 이른 순. 작성자를 함께 읽는다(N+1 방지). */
    @Query("""
            SELECT r FROM Report r
            JOIN FETCH r.user
            WHERE r.status = :status
              AND r.startsAt > :now AND r.startsAt <= :to
              AND (:buildingId IS NULL OR r.building.id = :buildingId)
            ORDER BY r.startsAt ASC, r.id ASC
            """)
    List<Report> findUpcomingReports(@Param("status") ReportStatus status, @Param("now") LocalDateTime now,
                                     @Param("to") LocalDateTime to, @Param("buildingId") Long buildingId);

    /**
     * (from, to] 사이에 시작한 ACTIVE 제보 중 시작 전에 승인된 것(reviewedAt &lt; startsAt) — 승인 때 새 제보 알림을
     * 미뤄 둔 제보들이다(ReportStartPushScheduler). 시작 뒤에 승인된 제보는 승인 때 이미 보냈으니 빠진다.
     * 작성자·건물을 함께 읽는다(트랜잭션 밖 푸시용 값 복사).
     */
    @Query("""
            SELECT r FROM Report r
            JOIN FETCH r.user
            JOIN FETCH r.building
            WHERE r.status = :status
              AND r.startsAt > :from AND r.startsAt <= :to
              AND r.endsAt > :to
              AND r.reviewedAt IS NOT NULL AND r.reviewedAt < r.startsAt
            ORDER BY r.startsAt ASC, r.id ASC
            """)
    List<Report> findStartedAfterEarlyApproval(@Param("status") ReportStatus status,
                                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT r FROM Report r
            WHERE (:buildingId IS NULL OR r.building.id = :buildingId)
            ORDER BY r.createdAt DESC
            """)
    List<Report> findAllByBuildingIdOptional(@Param("buildingId") Long buildingId);

    @Modifying
    @Query("UPDATE Report r SET r.status = :status WHERE r.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") ReportStatus status);

    void deleteByUser_Id(Long userId);

    /** 회원탈퇴 시 S3 사진을 지우려고 제보를 지우기 전에 키를 모은다. */
    @Query("SELECT i.imageKey FROM ReportImage i WHERE i.report.user.id = :userId")
    List<String> findImageKeysByUserId(@Param("userId") Long userId);

    /** 관리자 목록. status 가 null 이면 DELETED 를 뺀 전부. 작성자·건물을 함께 읽어 목록 N+1 을 막는다. */
    @Query("""
            SELECT r FROM Report r
            JOIN FETCH r.user
            JOIN FETCH r.building
            WHERE (:status IS NULL AND r.status <> com.hongmap.hongmapbackend.report.ReportStatus.DELETED)
               OR r.status = :status
            ORDER BY r.createdAt DESC
            """)
    List<Report> findForAdmin(@Param("status") ReportStatus status, Pageable pageable);

    long countByStatus(ReportStatus status);

    /** 다른 제보에 이미 붙은 사진 키인지(report_images.image_key 는 UNIQUE). */
    @Query("SELECT COUNT(i) > 0 FROM ReportImage i WHERE i.imageKey = :imageKey")
    boolean existsByImageKey(@Param("imageKey") String imageKey);
}
