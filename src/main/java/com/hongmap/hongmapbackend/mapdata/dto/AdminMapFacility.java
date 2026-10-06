package com.hongmap.hongmapbackend.mapdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 관리자 편의시설 응답 — map/data 의 facility 모양 + buildingCode. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminMapFacility(
        String id,
        String kind,
        String buildingCode,
        String buildingName,
        Integer floor,
        String note,
        Double lat,
        Double lng
) {
}
