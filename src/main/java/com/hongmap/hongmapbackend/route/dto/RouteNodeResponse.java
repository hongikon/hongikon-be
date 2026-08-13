package com.hongmap.hongmapbackend.route.dto;

import com.hongmap.hongmapbackend.route.entity.RouteNode;

import java.math.BigDecimal;

public record RouteNodeResponse(
        Long id,
        BigDecimal latitude,
        BigDecimal longitude,
        int floor,
        String nodeType,
        Long buildingId
) {
    public static RouteNodeResponse from(RouteNode node) {
        return new RouteNodeResponse(
                node.getId(),
                node.getLatitude(),
                node.getLongitude(),
                node.getFloor(),
                node.getNodeType(),
                node.getBuildingId()
        );
    }
}
