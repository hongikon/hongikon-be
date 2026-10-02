package com.hongmap.hongmapbackend.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 내 제보 내역 전용 조회. ReportRepository 와 같은 엔티티를 보지만, 작성자 본인 범위의 읽기 쿼리만 모아
 * 다른 기능(지도·관리자)의 쿼리와 섞이지 않게 따로 둔다.
 */
public interface MyReportRepository extends Repository<Report, Long> {

    /** 작성자 본인의 제보 전부(관리자가 지운 DELETED 포함), 최신 등록순. 건물 이름을 같이 읽어 N+1 을 막는다. */
    @Query(value = """
            SELECT r FROM Report r
            JOIN FETCH r.building
            WHERE r.user.id = :userId
            ORDER BY r.createdAt DESC, r.id DESC
            """,
            countQuery = "SELECT COUNT(r) FROM Report r WHERE r.user.id = :userId")
    Page<Report> findMine(@Param("userId") Long userId, Pageable pageable);

    long countByUser_IdAndStatus(Long userId, ReportStatus status);

    long countByUser_Id(Long userId);
}
