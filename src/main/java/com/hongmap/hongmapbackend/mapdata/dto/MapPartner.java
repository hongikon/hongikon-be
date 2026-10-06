package com.hongmap.hongmapbackend.mapdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * GET /map/data 의 제휴업체. 앱 Partner 타입과 같은 모양(null·빈 목록은 생략).
 * id 는 code. affiliationBenefits 는 소속 전용 혜택이 있는 소속만(예외).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapPartner(
        String id,
        String name,
        String category,
        List<String> affiliations,
        String mapIcon,
        Double lat,
        Double lng,
        String benefit,
        List<MapAffiliationBenefit> affiliationBenefits,
        String address,
        String hours,
        String contact,
        MapLink link
) {
}
