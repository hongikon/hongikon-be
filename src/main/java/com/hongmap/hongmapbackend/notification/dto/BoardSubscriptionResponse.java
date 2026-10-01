package com.hongmap.hongmapbackend.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hongmap.hongmapbackend.notification.UserBoardSubscription;

import java.time.LocalDateTime;

public record BoardSubscriptionResponse(
        String sourceId,
        boolean alertEnabled,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {
    public static BoardSubscriptionResponse of(UserBoardSubscription s) {
        return new BoardSubscriptionResponse(s.getSourceId(), s.isAlertEnabled(), s.getCreatedAt());
    }
}
