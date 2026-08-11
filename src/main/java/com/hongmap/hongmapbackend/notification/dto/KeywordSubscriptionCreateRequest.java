package com.hongmap.hongmapbackend.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KeywordSubscriptionCreateRequest(
        @NotBlank
        @Size(max = 30)
        String keyword
) {
}
