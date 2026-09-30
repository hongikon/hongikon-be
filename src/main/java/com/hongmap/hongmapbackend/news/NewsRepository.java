package com.hongmap.hongmapbackend.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    @Query("""
            SELECT n FROM News n
            WHERE (:category IS NULL OR n.category = :category)
              AND (:departmentId IS NULL OR n.department.id = :departmentId)
              AND (:buildingId IS NULL OR n.building.id = :buildingId)
            ORDER BY n.publishedAt DESC
            """)
    List<News> findFiltered(
            @Param("category") String category,
            @Param("departmentId") Long departmentId,
            @Param("buildingId") Long buildingId
    );

    boolean existsBySourceUrl(String sourceUrl);

    /**
     * 이미 저장된 글을 크롤러가 다시 만났을 때 source_id가 비어 있으면 채운다(자가 치유용).
     * 이미 값이 있는 행은 건드리지 않는다 — 먼저 기록된 게시판 출처를 유지한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE News n SET n.sourceId = :sourceId WHERE n.sourceUrl IN :sourceUrls AND n.sourceId IS NULL")
    int fillMissingSourceId(@Param("sourceId") String sourceId, @Param("sourceUrls") Collection<String> sourceUrls);

    @Query("SELECT n FROM News n WHERE n.department IS NULL OR n.building IS NULL")
    List<News> findAllMissingLocation();
}
