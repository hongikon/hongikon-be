package com.hongmap.hongmapbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenExchangeRequest(
        @NotBlank(message = "code는 필수입니다.") String code
) {
}
