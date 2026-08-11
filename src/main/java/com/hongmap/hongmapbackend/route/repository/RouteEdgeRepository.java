package com.hongmap.hongmapbackend.route.repository;

import com.hongmap.hongmapbackend.route.entity.RouteEdge;
import org.springframework.data.jpa.repository.JpaRepository;

// 그래프 로더가 findAll()로 전체 로드 후 인메모리 인접 리스트를 구성하는 용도.
// 이후 라우팅 엔진(Dijkstra/A*) 착수 시점에 추가 쿼리 메서드가 필요해질 수 있음.
public interface RouteEdgeRepository extends JpaRepository<RouteEdge, Long> {
}
