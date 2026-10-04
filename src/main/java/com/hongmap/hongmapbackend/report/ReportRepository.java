package com.hongmap.hongmapbackend.report;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 지도 목록(공개). 작성자 표시 이름·authorKey 를 만들려고 제보마다 users 를 따로 읽던 N+1 을 JOIN FETCH 로 없앤다.
     * ManyToOne 만 FETCH 하므로 행이 늘지 않고(중복 없음) 페이지(LIMIT)도 SQL 로 걸린다. 사진은 Report.images 의 @BatchSize 로 묶어 읽는다.
     */
    @Query("""
            SELECT r FROM Report r
            JOIN FETCH r.user
            WHERE r.status = :status
              AND :now BETWEEN r.startsAt AND r.endsAt
              AND (:buildingId IS NULL OR r.building.id = :buildingId)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Report> findLiveReports(@Param("status") ReportStatus status, @Param("now") LocalDateTime now,
                                  @Param("buildingId") Long buildingId, Pageable pageable);

    /** 등록 제한용 — 한 사용자의 특정 상태(승인 대기) 제보 수. */
    long countByUser_IdAndStatus(Long userId, ReportStatus status);

    @Query("""
            SELECT r FROM Report r
            WHERE (:buildingId IS NULL OR r.building.id = :buildingId)
            ORDER BY r.createdAt DESC
            """)
    List<Report> findAllByBuildingIdOptional(@Param("buildingId") Long buildingId);

    @Modifying
    @Query("UPDATE Report r SET r.status = :status WHERE r.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") ReportStatus status);

    /** 상태가 from일 때만 to로 바꾼다. 바뀐 행 수(0 또는 1) — 동시에 신고가 들어와도 자동 숨김을 한 번만 처리한다. */
    @Modifying
    @Query("UPDATE Report r SET r.status = :to WHERE r.id = :id AND r.status = :from")
    int updateStatusIf(@Param("id") Long id, @Param("from") ReportStatus from, @Param("to") ReportStatus to);

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

    /**
     * 승인 대기 리마인드 선점(AdminReportReminder). PENDING 이고 다음 단계에 도달한 제보의 admin_reminder_count 를 1 올리고
     * admin_reminded_at = now 로 찍는다. 바뀐 행 수를 돌려준다 — 0이면 보낼 게 없다(다른 서버가 이미 선점한 경우 포함).
     * <ul>
     *   <li>1단계: 아직 한 번도 안 들어갔고 created_at ≤ firstCutoff(지금 − 30분)</li>
     *   <li>2단계: 한 번 들어갔고 created_at ≤ secondCutoff(지금 − 2시간), 직전 리마인드가 repeatCutoff(지금 − 90분) 이전 —
     *       방해 금지 시간 뒤 08:00 요약에 처음 들어간 제보가 바로 다음 회차에 또 오지 않게 간격을 둔다.</li>
     * </ul>
     * 이미 끝난(ends_at ≤ now) 제보는 승인해도 지도에 뜨지 않으니 리마인드하지 않는다.
     * 행 잠금으로 직렬화되므로 동시에 돈 두 서버 중 하나만 1 이상을 받는다. @UpdateTimestamp(updated_at)는 건드리지 않는다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Report r
            SET r.adminReminderCount = r.adminReminderCount + 1, r.adminRemindedAt = :now
            WHERE r.status = com.hongmap.hongmapbackend.report.ReportStatus.PENDING
              AND r.endsAt > :now
              AND ((r.adminReminderCount = 0 AND r.createdAt <= :firstCutoff)
                OR (r.adminReminderCount = 1 AND r.createdAt <= :secondCutoff AND r.adminRemindedAt <= :repeatCutoff))
            """)
    int claimAdminReminders(@Param("now") LocalDateTime now, @Param("firstCutoff") LocalDateTime firstCutoff,
                            @Param("secondCutoff") LocalDateTime secondCutoff,
                            @Param("repeatCutoff") LocalDateTime repeatCutoff);

    /** 리마인드 본문용 — created_at ≤ cutoff 이고 아직 끝나지 않은(ends_at &gt; now) PENDING 제보 수. */
    long countByStatusAndCreatedAtLessThanEqualAndEndsAtAfter(ReportStatus status, LocalDateTime cutoff,
                                                              LocalDateTime now);

    /** 리마인드 본문용 — 아직 끝나지 않은 PENDING 제보 중 가장 오래된 것. */
    Optional<Report> findFirstByStatusAndEndsAtAfterOrderByCreatedAtAscIdAsc(ReportStatus status, LocalDateTime now);
}
