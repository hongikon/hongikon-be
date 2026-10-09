package com.hongmap.hongmapbackend.mapdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PathNodeRepository extends JpaRepository<PathNode, Long> {

    /** 지도 데이터·점검용 — 출입구 노드의 건물까지 한 번에. code 순. */
    @Query("SELECT n FROM PathNode n LEFT JOIN FETCH n.building ORDER BY n.code ASC")
    List<PathNode> findAllWithBuilding();

    @Query("SELECT n FROM PathNode n LEFT JOIN FETCH n.building WHERE n.code = :code")
    Optional<PathNode> findByCodeWithBuilding(@Param("code") String code);

    boolean existsByCode(String code);

    boolean existsByBuildingIdAndEntranceLabel(Long buildingId, String entranceLabel);
}
