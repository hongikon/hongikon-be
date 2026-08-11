package com.hongmap.hongmapbackend.partner.dto;

import java.util.List;

public record PartnerListResponse(
    List<PartnerResponse> partners,
    int count
) {
    public static PartnerListResponse of(List<PartnerResponse> partners) {
        return new PartnerListResponse(partners, partners.size());
    }
}
