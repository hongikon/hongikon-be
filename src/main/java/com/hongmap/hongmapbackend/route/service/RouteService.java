package com.hongmap.hongmapbackend.route.service;

import com.hongmap.hongmapbackend.route.dto.RouteEdgeSegmentResponse;
import com.hongmap.hongmapbackend.route.dto.RouteNodeResponse;
import com.hongmap.hongmapbackend.route.dto.RoutePathResponse;
import com.hongmap.hongmapbackend.route.dto.RouteSearchRequest;
import com.hongmap.hongmapbackend.route.entity.RouteNode;
import com.hongmap.hongmapbackend.route.graph.RouteEdgeView;
import com.hongmap.hongmapbackend.route.graph.RouteGraph;
import com.hongmap.hongmapbackend.route.graph.RouteGraphLoader;
import com.hongmap.hongmapbackend.route.routing.DijkstraRoutingEngine;
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

    public RoutePathResponse findShortestPath(RouteSearchRequest request) {
        RouteGraph graph = routeGraphLoader.getGraph();

        RouteNode startNode = getNodeOrThrow(graph, request.startNodeId());
        RouteNode endNode = getNodeOrThrow(graph, request.endNodeId());

        DijkstraRoutingEngine.PathResult result = dijkstraRoutingEngine
                .findShortestPath(graph, startNode.getId(), endNode.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "두 지점을 연결하는 경로를 찾을 수 없습니다."));

        return toResponse(graph, result);
    }

    private RouteNode getNodeOrThrow(RouteGraph graph, Long nodeId) {
        RouteNode node = graph.getNode(nodeId);
        if (node == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 노드입니다. id=" + nodeId);
        }
        return node;

    }

    private RoutePathResponse toResponse(RouteGraph graph, DijkstraRoutingEngine.PathResult result) {
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

        return RoutePathResponse.of(result.totalDistanceM(), nodeResponses, edgeResponses);
    }
}
