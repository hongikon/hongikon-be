package com.hongmap.hongmapbackend.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    @Query("""
            SELECT n FROM News n
            WHERE (:category IS NULL OR n.category = :category)
              AND (:departmentId IS NULL OR n.departmentId = :departmentId)
              AND (:buildingId IS NULL OR n.building.id = :buildingId)
            ORDER BY n.publishedAt DESC
            """)
    List<News> findFiltered(
            @Param("category") String category,
            @Param("departmentId") Long departmentId,
            @Param("buildingId") Long buildingId
    );

    boolean existsBySourceUrl(String sourceUrl);
}
