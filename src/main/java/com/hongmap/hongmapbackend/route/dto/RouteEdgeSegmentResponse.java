package com.hongmap.hongmapbackend.route.dto;

import com.hongmap.hongmapbackend.route.graph.RouteEdgeView;

import java.math.BigDecimal;

public record RouteEdgeSegmentResponse(
        Long edgeId,
        Long fromNodeId,
        Long toNodeId,
        BigDecimal distanceM,
        boolean hasRoof,
        boolean isBarrierFree,
        String edgeType
) {
    public static RouteEdgeSegmentResponse of(RouteEdgeView edge, Long fromNodeId, Long toNodeId) {
        return new RouteEdgeSegmentResponse(
                edge.edgeId(),
                fromNodeId,
                toNodeId,
                edge.distanceM(),
                edge.hasRoof(),
                edge.isBarrierFree(),
                edge.edgeType()
        );
    }
}
