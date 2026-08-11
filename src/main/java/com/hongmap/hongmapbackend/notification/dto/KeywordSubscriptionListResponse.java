package com.hongmap.hongmapbackend.notification.dto;

import java.util.List;

public record KeywordSubscriptionListResponse(
        List<KeywordSubscriptionResponse> keywords
) {
}
