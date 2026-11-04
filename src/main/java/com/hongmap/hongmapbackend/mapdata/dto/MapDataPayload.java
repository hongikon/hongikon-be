package com.hongmap.hongmapbackend.mapdata.dto;

import java.util.List;

/** version 을 계산할 본문(version 제외). 필드 순서가 곧 직렬화 순서다. */
public record MapDataPayload(
        /** 근처 제휴 알림 긴급 스위치(hongikon.nearby-alerts.enabled). version 에 들어가 바꾸면 앱이 새 본문을 받는다. */
        boolean nearbyAlertsEnabled,
        List<MapBuilding> buildings,
        List<MapFacility> facilities,
        List<MapPartner> partners,
        List<MapExhibition> exhibitions,
        MapPaths paths
) {
}
