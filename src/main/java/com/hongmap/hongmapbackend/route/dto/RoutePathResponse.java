package com.hongmap.hongmapbackend.route.dto;

import java.math.BigDecimal;
import java.util.List;

public record RoutePathResponse(
        BigDecimal totalDistanceM,
        List<RouteNodeResponse> nodes,
        List<RouteEdgeSegmentResponse> edges
) {
    public static RoutePathResponse of(BigDecimal totalDistanceM,
                                        List<RouteNodeResponse> nodes,
                                        List<RouteEdgeSegmentResponse> edges) {
        return new RoutePathResponse(totalDistanceM, nodes, edges);
    }
}
