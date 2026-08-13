package com.hongmap.hongmapbackend.route.graph;

import com.hongmap.hongmapbackend.route.entity.RouteNode;

import java.util.List;
import java.util.Map;

// RouteGraphLoader가 구성한 불변 인메모리 그래프 스냅샷.
public final class RouteGraph {

    private final Map<Long, RouteNode> nodesById;
    private final Map<Long, List<RouteEdgeView>> adjacency;

    RouteGraph(Map<Long, RouteNode> nodesById, Map<Long, List<RouteEdgeView>> adjacency) {
        this.nodesById = nodesById;
        this.adjacency = adjacency;
    }

    public RouteNode getNode(Long nodeId) {
        return nodesById.get(nodeId);
    }

    public boolean containsNode(Long nodeId) {
        return nodesById.containsKey(nodeId);
    }

    public List<RouteEdgeView> neighbors(Long nodeId) {
        return adjacency.getOrDefault(nodeId, List.of());
    }

    public int nodeCount() {
        return nodesById.size();
    }
}
