package com.hongmap.hongmapbackend.mapdata.dto;

import java.util.List;

/** version 을 계산할 본문(version 제외). 필드 순서가 곧 직렬화 순서다. */
public record MapDataPayload(
        List<MapBuilding> buildings,
        List<MapFacility> facilities,
        List<MapPartner> partners,
        List<MapExhibition> exhibitions
) {
}
