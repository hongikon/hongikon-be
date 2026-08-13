package com.hongmap.hongmapbackend.route.graph;

import com.hongmap.hongmapbackend.route.entity.RouteEdge;
import com.hongmap.hongmapbackend.route.entity.RouteNode;
import com.hongmap.hongmapbackend.route.repository.RouteEdgeRepository;
import com.hongmap.hongmapbackend.route.repository.RouteNodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// RouteNode/RouteEdge 전체를 DB에서 로드해 인메모리 인접 리스트로 구성한다.
// RouteEdge는 DB에 단방향 row로 저장되므로, 로드 시 양쪽 노드 모두에 인접 항목을 추가해 양방향 순회가 가능하게 만든다.
@Component
@RequiredArgsConstructor
public class RouteGraphLoader {

    private final RouteNodeRepository routeNodeRepository;
    private final RouteEdgeRepository routeEdgeRepository;

    private volatile RouteGraph graph = new RouteGraph(Map.of(), Map.of());

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        List<RouteNode> nodes = routeNodeRepository.findAll();
        List<RouteEdge> edges = routeEdgeRepository.findAll();

        Map<Long, RouteNode> nodesById = new HashMap<>();
        for (RouteNode node : nodes) {
            nodesById.put(node.getId(), node);
        }

        Map<Long, List<RouteEdgeView>> adjacency = new HashMap<>();
        for (RouteEdge edge : edges) {
            addAdjacency(adjacency, edge.getFromNodeId(), edge.getToNodeId(), edge);
            addAdjacency(adjacency, edge.getToNodeId(), edge.getFromNodeId(), edge);
        }

        this.graph = new RouteGraph(Map.copyOf(nodesById), adjacency);
    }

    private void addAdjacency(Map<Long, List<RouteEdgeView>> adjacency, Long fromNodeId, Long toNodeId, RouteEdge edge) {
        RouteEdgeView view = new RouteEdgeView(
                edge.getId(),
                toNodeId,
                edge.getDistanceM(),
                edge.isHasRoof(),
                edge.isBarrierFree(),
                edge.getEdgeType()
        );
        adjacency.computeIfAbsent(fromNodeId, key -> new ArrayList<>()).add(view);
    }

    public RouteGraph getGraph() {
        return graph;
    }
}
