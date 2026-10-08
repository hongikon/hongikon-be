package com.hongmap.hongmapbackend.mapdata.dto;

import java.util.List;

/** GET /map/data 응답. version = 나머지 본문(MapDataPayload) 직렬화의 SHA-256 앞 16 hex. */
public record MapDataResponse(
        String version,
        List<MapBuilding> buildings,
        List<MapFacility> facilities,
        List<MapPartner> partners,
        List<MapExhibition> exhibitions
) {
    public static MapDataResponse of(String version, MapDataPayload payload) {
        return new MapDataResponse(version, payload.buildings(), payload.facilities(), payload.partners(),
                payload.exhibitions());
    }
}
