package com.hongmap.hongmapbackend.notification.dto;

import java.util.List;

public record NotificationCategoryListResponse(
        List<NotificationCategoryResponse> categories
) {
}
