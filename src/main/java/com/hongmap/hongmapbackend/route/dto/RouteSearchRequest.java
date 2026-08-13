package com.hongmap.hongmapbackend.route.dto;

import jakarta.validation.constraints.NotNull;

public record RouteSearchRequest(
        @NotNull Long startNodeId,
        @NotNull Long endNodeId
) {
}
