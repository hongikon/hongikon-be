package com.hongmap.hongmapbackend.mapdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExhibitionRepository extends JpaRepository<Exhibition, Long> {

    /**
     * 지도 데이터용 — 끝나지 않았고(endsOn >= today) horizon 안에 시작하는(startsOn <= horizon) 전시 중
     * 지금 있는 장소(campus_facilities.code)의 것만. 장소 → 시작일 → id 순.
     */
    @Query("SELECT e FROM Exhibition e WHERE e.endsOn >= :today AND e.startsOn <= :horizon"
            + " AND EXISTS (SELECT 1 FROM CampusFacility f WHERE f.code = e.facilityCode)"
            + " ORDER BY e.facilityCode ASC, e.startsOn ASC, e.id ASC")
    List<Exhibition> findForMap(@Param("today") LocalDate today, @Param("horizon") LocalDate horizon);

    /** 관리자 목록 — 지난 전시 포함, 최근 시작한 것부터. */
    List<Exhibition> findAllByOrderByStartsOnDescIdDesc();
}
