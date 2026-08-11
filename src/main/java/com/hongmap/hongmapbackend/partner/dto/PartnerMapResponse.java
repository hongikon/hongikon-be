package com.hongmap.hongmapbackend.partner.dto;

import java.util.List;

public record PartnerMapResponse(
    List<PartnerSummaryResponse> partners,
    int count
) {
    public static PartnerMapResponse of(List<PartnerSummaryResponse> partners) {
        return new PartnerMapResponse(partners, partners.size());
    }
}
