package com.hongmap.hongmapbackend.mapdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** GET /map/data 의 편의시설. id 는 code, buildingName 은 건물 표시 이름. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapFacility(
        String id,
        String kind,
        String buildingName,
        Integer floor,
        String note,
        Double lat,
        Double lng
) {
}
