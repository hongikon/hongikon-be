package com.hongmap.hongmapbackend.route.service;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.route.dto.RouteEdgeSegmentResponse;
import com.hongmap.hongmapbackend.route.dto.RouteNodeResponse;
import com.hongmap.hongmapbackend.route.dto.RoutePathResponse;
import com.hongmap.hongmapbackend.route.dto.RouteSearchRequest;
import com.hongmap.hongmapbackend.route.dto.RouteSimplifiedStepResponse;
import com.hongmap.hongmapbackend.route.entity.RouteNode;
import com.hongmap.hongmapbackend.route.graph.RouteEdgeView;
import com.hongmap.hongmapbackend.route.graph.RouteGraph;
import com.hongmap.hongmapbackend.route.graph.RouteGraphLoader;
import com.hongmap.hongmapbackend.route.repository.RouteNodeRepository;
import com.hongmap.hongmapbackend.route.routing.DijkstraRoutingEngine;
import com.hongmap.hongmapbackend.route.routing.RoutePathSimplifier;
import com.hongmap.hongmapbackend.route.routing.RouteTimeEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteGraphLoader routeGraphLoader;
    private final DijkstraRoutingEngine dijkstraRoutingEngine;
    private final RouteTimeEstimator routeTimeEstimator;
    private final RoutePathSimplifier routePathSimplifier;
    private final BuildingRepository buildingRepository;
    private final RouteNodeRepository routeNodeRepository;

    public RoutePathResponse findShortestPath(RouteSearchRequest request) {
        RouteGraph graph = routeGraphLoader.getGraph();

        Long startNodeId = resolveNodeId(request.startBuildingName(), request.startFloor());
        Long endNodeId = resolveNodeId(request.endBuildingName(), request.endFloor());

        RouteNode startNode = getNodeOrThrow(graph, startNodeId);
        RouteNode endNode = getNodeOrThrow(graph, endNodeId);

        DijkstraRoutingEngine.PathResult result = dijkstraRoutingEngine
                .findShortestPath(graph, startNode.getId(), endNode.getId(), request.useElevatorOrDefault())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "두 지점을 연결하는 경로를 찾을 수 없습니다."));

        return toResponse(graph, result, request.simplifiedOrDefault());
    }

    // 건물명+층을 해당 건물+층의 대표 접속점(RouteNode) id로 변환한다.
    // 같은 건물+층에 접속점이 여러 개면 point_no 오름차순 기준 첫 번째 노드를 사용한다.
    private Long resolveNodeId(String buildingName, int floor) {
        Building building = buildingRepository.findByName(buildingName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "건물 '" + buildingName + "'를 찾을 수 없습니다"));

        RouteNode node = routeNodeRepository
                .findFirstByBuildingIdAndFloorOrderByPointNoAsc(building.getId(), floor)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "건물 '" + buildingName + "'의 " + floor + "층에 접속점이 없습니다"));

        return node.getId();
    }

    private RouteNode getNodeOrThrow(RouteGraph graph, Long nodeId) {
        RouteNode node = graph.getNode(nodeId);
        if (node == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 노드입니다. id=" + nodeId);
        }
        return node;

    }

    private RoutePathResponse toResponse(RouteGraph graph, DijkstraRoutingEngine.PathResult result, boolean simplified) {
        List<Long> nodeIds = result.nodeIds();

        List<RouteNodeResponse> nodeResponses = nodeIds.stream()
                .map(graph::getNode)
                .map(RouteNodeResponse::from)
                .toList();

        List<RouteEdgeSegmentResponse> edgeResponses = new ArrayList<>();
        List<RouteEdgeView> edges = result.edges();
        for (int i = 0; i < edges.size(); i++) {
            edgeResponses.add(RouteEdgeSegmentResponse.of(edges.get(i), nodeIds.get(i), nodeIds.get(i + 1)));
        }

        long estimatedTimeSeconds = routeTimeEstimator.estimateSeconds(result.totalDistanceM(), edges);
        List<RouteSimplifiedStepResponse> simplifiedSteps =
                simplified ? routePathSimplifier.simplify(nodeIds, edges) : null;

        return RoutePathResponse.of(result.totalDistanceM(), estimatedTimeSeconds, nodeResponses, edgeResponses, simplifiedSteps);
    }
}
