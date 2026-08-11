package com.hongmap.hongmapbackend.route.repository;

import com.hongmap.hongmapbackend.route.entity.RouteNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteNodeRepository extends JpaRepository<RouteNode, Long> {

    List<RouteNode> findByBuildingId(Long buildingId);
}
