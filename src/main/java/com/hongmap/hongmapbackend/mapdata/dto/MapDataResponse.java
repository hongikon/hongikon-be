package com.hongmap.hongmapbackend.mapdata.dto;

import java.util.List;

/**
 * GET /map/data 응답. version = 나머지 본문(MapDataPayload) 직렬화의 SHA-256 앞 16 hex.
 * nearbyAlertsEnabled 는 null 이 아닌 boolean 이라 항상 나간다.
 */
public record MapDataResponse(
        String version,
        /** 근처 제휴 알림 긴급 스위치. false 면 앱은 nearbyAlert 업체가 있어도 알림을 띄우지 않는다. */
        boolean nearbyAlertsEnabled,
        List<MapBuilding> buildings,
        List<MapFacility> facilities,
        List<MapPartner> partners,
        List<MapExhibition> exhibitions,
        MapPaths paths
) {
    public static MapDataResponse of(String version, MapDataPayload payload) {
        return new MapDataResponse(version, payload.nearbyAlertsEnabled(), payload.buildings(), payload.facilities(), payload.partners(),
                payload.exhibitions(), payload.paths());
    }
}
