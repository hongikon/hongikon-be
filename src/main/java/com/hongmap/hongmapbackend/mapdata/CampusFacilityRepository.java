package com.hongmap.hongmapbackend.mapdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampusFacilityRepository extends JpaRepository<CampusFacility, Long> {

    /** 지도 데이터용 — 건물 이름이 필요하므로 건물까지 한 번에(N+1 방지). */
    @Query("SELECT f FROM CampusFacility f JOIN FETCH f.building ORDER BY f.sortOrder ASC, f.id ASC")
    List<CampusFacility> findAllForMap();

    @Query("SELECT f FROM CampusFacility f JOIN FETCH f.building WHERE f.code = :code")
    Optional<CampusFacility> findByCodeWithBuilding(@Param("code") String code);

    boolean existsByCode(String code);

    @Query("SELECT COALESCE(MAX(f.sortOrder), 0) FROM CampusFacility f")
    int findMaxSortOrder();
}
