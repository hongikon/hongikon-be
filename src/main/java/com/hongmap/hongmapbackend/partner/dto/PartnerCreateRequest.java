package com.hongmap.hongmapbackend.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;

public record PartnerCreateRequest(
    @NotBlank String name,
    @NotBlank String category,
    @NotNull BigDecimal latitude,
    @NotNull BigDecimal longitude,
    String benefit,
    String address,
    String roadAddress,
    String hours,
    String contact,
    String mapIcon,
    String linkLabel,
    String linkUrl,
    Set<String> affiliations
) {}
