package com.hongmap.hongmapbackend.mapdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * GET /map/data 의 전시(관리자 API 도 같은 모양). facilityId 는 편의시설 code, 날짜는 KST "yyyy-MM-dd"(양 끝 포함).
 * link 는 url 이 있을 때만.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapExhibition(
        Long id,
        String facilityId,
        String title,
        String startsOn,
        String endsOn,
        String hours,
        String description,
        MapLink link
) {
}
