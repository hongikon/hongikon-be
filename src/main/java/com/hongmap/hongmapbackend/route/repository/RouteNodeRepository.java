package com.hongmap.hongmapbackend.route.repository;

import com.hongmap.hongmapbackend.route.entity.RouteNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouteNodeRepository extends JpaRepository<RouteNode, Long> {

    List<RouteNode> findByBuildingId(Long buildingId);

    Optional<RouteNode> findByBuildingIdAndFloorAndPointNo(Long buildingId, int floor, String pointNo);

    Optional<RouteNode> findFirstByBuildingIdAndFloorOrderByPointNoAsc(Long buildingId, int floor);
}
