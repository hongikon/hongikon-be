package com.hongmap.hongmapbackend.route.dto;

import java.math.BigDecimal;

// 연속된 같은 edge_type 구간을 하나로 합친 간략 경로 스텝.
public record RouteSimplifiedStepResponse(
        String edgeType,
        BigDecimal distanceM,
        int stepCount,
        Long fromNodeId,
        Long toNodeId
) {
}
