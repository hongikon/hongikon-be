package com.hongmap.hongmapbackend.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackCreateRequest(
        @NotBlank @Size(max = 1000) String content,
        @Size(max = 100) String contact
) {
}
