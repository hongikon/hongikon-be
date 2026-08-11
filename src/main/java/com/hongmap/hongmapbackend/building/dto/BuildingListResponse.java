package com.hongmap.hongmapbackend.building.dto;

import java.util.List;

public record BuildingListResponse(
        List<BuildingResponse> buildings
) {
}
