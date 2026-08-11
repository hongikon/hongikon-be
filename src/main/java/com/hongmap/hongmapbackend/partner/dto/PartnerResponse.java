package com.hongmap.hongmapbackend.partner.dto;

import com.hongmap.hongmapbackend.partner.entity.Partner;

import java.math.BigDecimal;
import java.util.Set;

public record PartnerResponse(
    Long id,
    String name,
    String category,
    BigDecimal latitude,
    BigDecimal longitude,
    String benefit,
    String address,
    String roadAddress,
    String hours,
    String contact,
    String mapIcon,
    String linkLabel,
    String linkUrl,
    Set<String> affiliations
) {
    public static PartnerResponse from(Partner partner) {
        return new PartnerResponse(
            partner.getId(),
            partner.getName(),
            partner.getCategory(),
            partner.getLatitude(),
            partner.getLongitude(),
            partner.getBenefit(),
            partner.getAddress(),
            partner.getRoadAddress(),
            partner.getHours(),
            partner.getContact(),
            partner.getMapIcon(),
            partner.getLinkLabel(),
            partner.getLinkUrl(),
            partner.getAffiliations()
        );
    }
}
