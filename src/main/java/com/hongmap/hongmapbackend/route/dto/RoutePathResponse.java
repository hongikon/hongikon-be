package com.hongmap.hongmapbackend.route.dto;

import java.math.BigDecimal;
import java.util.List;

public record RoutePathResponse(
        BigDecimal totalDistanceM,
        long estimatedTimeSeconds,
        List<RouteNodeResponse> nodes,
        List<RouteEdgeSegmentResponse> edges,
        List<RouteSimplifiedStepResponse> simplifiedSteps
) {
    public static RoutePathResponse of(BigDecimal totalDistanceM,
                                        long estimatedTimeSeconds,
                                        List<RouteNodeResponse> nodes,
                                        List<RouteEdgeSegmentResponse> edges,
                                        List<RouteSimplifiedStepResponse> simplifiedSteps) {
        return new RoutePathResponse(totalDistanceM, estimatedTimeSeconds, nodes, edges, simplifiedSteps);
    }
}
