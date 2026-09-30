package com.hongmap.hongmapbackend.feedback.dto;

import jakarta.validation.constraints.NotBlank;

/** @param status OPEN / RESOLVED */
public record FeedbackStatusRequest(@NotBlank String status) {
}
