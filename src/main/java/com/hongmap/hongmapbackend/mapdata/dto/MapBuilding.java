package com.hongmap.hongmapbackend.mapdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

/**
 * GET /map/data 의 건물. 앱 Building 타입과 같은 모양(null 필드는 생략).
 * name 은 표시 이름(display_name, 없으면 name). JSON 컬럼(facilities·boundary·extraBoundaries·entrances)은 그대로 싣는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapBuilding(
        Long id,
        String code,
        String name,
        Double lat,
        Double lng,
        String color,
        String category,
        String type,
        Integer floors,
        Integer basementFloors,
        String hours,
        String description,
        JsonNode facilities,
        String contact,
        MapLink link,
        JsonNode boundary,
        JsonNode extraBoundaries,
        JsonNode entrances
) {
}
