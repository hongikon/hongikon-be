package com.hongmap.hongmapbackend.partner.dto;

import com.hongmap.hongmapbackend.partner.entity.PartnerAffiliation;

// benefit은 소속 전용 혜택이 없으면 업체 기본 benefit으로 채워진 최종값이다.
public record PartnerAffiliationResponse(
    String affiliation,
    String benefit
) {
    public static PartnerAffiliationResponse from(PartnerAffiliation affiliation) {
        return new PartnerAffiliationResponse(
            affiliation.getAffiliation(),
            affiliation.getEffectiveBenefit()
        );
    }
}
