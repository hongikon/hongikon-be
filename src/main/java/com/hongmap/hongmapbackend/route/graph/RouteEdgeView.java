package com.hongmap.hongmapbackend.route.graph;

import java.math.BigDecimal;

// RouteEdge의 단방향 DB row 하나를 양방향 인접 리스트로 펼친 뷰.
// neighborNodeId는 이 뷰가 속한 노드를 기준으로 한 상대편 노드를 가리킨다.
public record RouteEdgeView(
        Long edgeId,
        Long neighborNodeId,
        BigDecimal distanceM,
        boolean hasRoof,
        boolean isBarrierFree,
        String edgeType
) {
}
