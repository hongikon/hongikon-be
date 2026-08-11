package com.hongmap.hongmapbackend.building;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    @Query("""
            SELECT p FROM Place p
            WHERE (:buildingId IS NULL OR p.building.id = :buildingId)
              AND (:category IS NULL OR p.category = :category)
              AND p.isActive = true
            ORDER BY p.building.id, p.floor
            """)
    List<Place> findFiltered(@Param("buildingId") Long buildingId, @Param("category") String category);
}
