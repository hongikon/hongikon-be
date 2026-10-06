package com.hongmap.hongmapbackend.mapdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AffiliationBenefitRequest(
        @NotBlank(message = "소속을 골라 주세요.") String affiliation,
        @NotBlank(message = "소속 혜택을 입력해 주세요.")
        @Size(max = 255, message = "혜택은 255자 이하로 입력해 주세요.") String benefit
) {
}
