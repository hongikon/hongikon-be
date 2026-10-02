package com.hongmap.hongmapbackend.notification.dto;

import jakarta.validation.constraints.NotNull;

public record BoardSubscriptionUpsertRequest(
        @NotNull(message = "alertEnabled는 필수입니다.")
        Boolean alertEnabled
) {
}
