package com.hongmap.hongmapbackend.mapdata.dto;

import java.util.List;

/**
 * GET /admin/map/path-audit — 경로망 점검.
 * - brokenEntranceRefs: 출입구 노드가 가리키는 라벨이 건물 entrances 에 없다(reason LABEL_MISSING / ENTRANCES_INVALID).
 *   이 점과 이어진 간선은 GET /map/data 에서 빠진다.
 * - isolatedNodes: 간선이 하나도 없는 점.
 * - detachedComponents: 본망(가장 큰 덩어리)과 이어지지 않은 덩어리(크기 2 이상, 큰 것부터). nodes 는 앞 20개까지.
 * - unlinkedEntrances: 경로망에 출입구 노드가 없는 건물 출입구(참고).
 * - longEdges: longEdgeThresholdM 보다 긴 간선(좌표 실수 의심).
 */
public record PathAuditResponse(
        Summary summary,
        List<BrokenEntranceRef> brokenEntranceRefs,
        List<String> isolatedNodes,
        List<Component> detachedComponents,
        List<EntranceRef> unlinkedEntrances,
        double longEdgeThresholdM,
        List<AdminPathEdge> longEdges
) {
    public record Summary(int nodes, int edges, int components, int mainComponentSize) {
    }

    public record BrokenEntranceRef(String node, String building, String label, String reason) {
    }

    public record Component(int size, List<String> nodes) {
    }

    public record EntranceRef(String building, String label) {
    }
}
