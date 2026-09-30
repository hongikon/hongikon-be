package com.hongmap.hongmapbackend.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// benefit을 비우면 업체 기본 benefit을 따른다.
public record PartnerAffiliationRequest(
    @NotBlank @Size(max = 50) String affiliation,
    @Size(max = 255) String benefit
) {}
