package com.hongmap.hongmapbackend.mapdata.dto;

import java.util.List;

/**
 * GET /map/data 의 paths — 앱이 길찾기를 계산하는 경로망. 비어 있어도 항상 나간다({"nodes":[],"edges":[]}).
 * edges 는 양방향 간선 [노드 id, 노드 id] (작은 id 먼저).
 */
public record MapPaths(
        List<MapPathNode> nodes,
        List<List<String>> edges
) {
    public static final MapPaths EMPTY = new MapPaths(List.of(), List.of());
}
