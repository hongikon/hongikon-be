package com.hongmap.hongmapbackend.partner.dto;

import com.hongmap.hongmapbackend.partner.entity.Partner;

import java.math.BigDecimal;

// 지도 마커 렌더링 전용 - 상세 정보 없이 가벼운 응답
public record PartnerSummaryResponse(
    Long id,
    String name,
    String category,
    BigDecimal latitude,
    BigDecimal longitude,
    String mapIcon
) {
    public static PartnerSummaryResponse from(Partner partner) {
        return new PartnerSummaryResponse(
            partner.getId(),
            partner.getName(),
            partner.getCategory(),
            partner.getLatitude(),
            partner.getLongitude(),
            partner.getMapIcon()
        );
    }
}
