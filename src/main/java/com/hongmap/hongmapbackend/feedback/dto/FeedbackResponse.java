package com.hongmap.hongmapbackend.feedback.dto;

import com.hongmap.hongmapbackend.feedback.Feedback;

import java.time.LocalDateTime;

public record FeedbackResponse(
        Long id,
        String content,
        String contact,
        Long userId,
        String userNickname,
        String status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
    public static FeedbackResponse of(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getId(),
                feedback.getContent(),
                feedback.getContact(),
                feedback.getUser() != null ? feedback.getUser().getId() : null,
                feedback.getUser() != null ? feedback.getUser().getNickname() : null,
                feedback.getStatus().name(),
                feedback.getCreatedAt(),
                feedback.getResolvedAt());
    }
}
