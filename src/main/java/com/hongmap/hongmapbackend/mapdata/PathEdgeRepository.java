package com.hongmap.hongmapbackend.mapdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PathEdgeRepository extends JpaRepository<PathEdge, Long> {

    /** 지도 데이터·점검용 — 양 끝 노드까지 한 번에. id 순. */
    @Query("SELECT e FROM PathEdge e JOIN FETCH e.nodeA JOIN FETCH e.nodeB ORDER BY e.id ASC")
    List<PathEdge> findAllWithNodes();

    boolean existsByNodeAAndNodeB(PathNode nodeA, PathNode nodeB);

    @Query("SELECT COUNT(e) FROM PathEdge e WHERE e.nodeA = :node OR e.nodeB = :node")
    long countByNode(@Param("node") PathNode node);
}
