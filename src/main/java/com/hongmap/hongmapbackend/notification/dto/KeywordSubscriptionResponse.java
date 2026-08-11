package com.hongmap.hongmapbackend.notification.dto;

import com.hongmap.hongmapbackend.notification.KeywordSubscription;
import lombok.Builder;

@Builder
public record KeywordSubscriptionResponse(
        Long id,
        String keyword
) {
    public static KeywordSubscriptionResponse of(KeywordSubscription k) {
        return KeywordSubscriptionResponse.builder()
                .id(k.getId())
                .keyword(k.getKeyword())
                .build();
    }
}
