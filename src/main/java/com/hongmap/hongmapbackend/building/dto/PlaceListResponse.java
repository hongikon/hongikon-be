package com.hongmap.hongmapbackend.building.dto;

import java.util.List;

public record PlaceListResponse(
        List<PlaceResponse> places
) {
}
