package com.hongmap.hongmapbackend.route.routing;

import com.hongmap.hongmapbackend.route.graph.RouteEdgeView;
import com.hongmap.hongmapbackend.route.graph.RouteGraph;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

// RouteGraph 위에서 두 노드 사이의 최단 거리 경로를 계산하는 다익스트라 알고리즘 구현.
// RouteEdge의 distanceM을 가중치로 사용하며, RouteGraph가 이미 양방향 인접 리스트로 구성되어 있으므로
// 이 엔진은 방향성을 신경 쓰지 않고 순수하게 그래프 탐색만 수행한다.
@Component
public class DijkstraRoutingEngine {

    public Optional<PathResult> findShortestPath(RouteGraph graph, Long startNodeId, Long endNodeId) {
        if (startNodeId.equals(endNodeId)) {
            return Optional.of(new PathResult(List.of(startNodeId), List.of(), BigDecimal.ZERO));
        }

        Map<Long, BigDecimal> distances = new HashMap<>();
        Map<Long, Long> previousNode = new HashMap<>();
        Map<Long, RouteEdgeView> previousEdge = new HashMap<>();
        Set<Long> visited = new HashSet<>();

        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparing(NodeDistance::distance));
        distances.put(startNodeId, BigDecimal.ZERO);
        queue.add(new NodeDistance(startNodeId, BigDecimal.ZERO));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            Long currentId = current.nodeId();

            if (!visited.add(currentId)) {
                continue;
            }
            if (currentId.equals(endNodeId)) {
                break;
            }

            for (RouteEdgeView edge : graph.neighbors(currentId)) {
                Long neighborId = edge.neighborNodeId();
                if (visited.contains(neighborId)) {
                    continue;
                }

                BigDecimal newDistance = distances.get(currentId).add(edge.distanceM());
                BigDecimal knownDistance = distances.get(neighborId);
                if (knownDistance == null || newDistance.compareTo(knownDistance) < 0) {
                    distances.put(neighborId, newDistance);
                    previousNode.put(neighborId, currentId);
                    previousEdge.put(neighborId, edge);
                    queue.add(new NodeDistance(neighborId, newDistance));
                }
            }
        }

        if (!distances.containsKey(endNodeId)) {
            return Optional.empty();
        }

        return Optional.of(buildPath(startNodeId, endNodeId, distances, previousNode, previousEdge));
    }

    private PathResult buildPath(Long startNodeId, Long endNodeId,
                                  Map<Long, BigDecimal> distances,
                                  Map<Long, Long> previousNode,
                                  Map<Long, RouteEdgeView> previousEdge) {
        List<Long> nodeIds = new ArrayList<>();
        List<RouteEdgeView> edges = new ArrayList<>();

        Long cursor = endNodeId;
        while (cursor != null) {
            nodeIds.add(cursor);
            RouteEdgeView edge = previousEdge.get(cursor);
            if (edge != null) {
                edges.add(edge);
            }
            cursor = previousNode.get(cursor);
        }

        Collections.reverse(nodeIds);
        Collections.reverse(edges);

        return new PathResult(nodeIds, edges, distances.get(endNodeId));
    }

    private record NodeDistance(Long nodeId, BigDecimal distance) {
    }

    public record PathResult(List<Long> nodeIds, List<RouteEdgeView> edges, BigDecimal totalDistanceM) {
    }
}
