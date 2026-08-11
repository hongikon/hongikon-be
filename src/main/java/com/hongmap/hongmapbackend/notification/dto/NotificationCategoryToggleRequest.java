package com.hongmap.hongmapbackend.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationCategoryToggleRequest(
        @NotNull
        Boolean enabled
) {
}
