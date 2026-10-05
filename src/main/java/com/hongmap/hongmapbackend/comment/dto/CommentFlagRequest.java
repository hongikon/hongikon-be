package com.hongmap.hongmapbackend.comment.dto;

import jakarta.validation.constraints.NotBlank;

/** reason: FALSE_INFO / SPAM / INAPPROPRIATE / PRIVACY / ETC (제보 신고와 같음) */
public record CommentFlagRequest(
        @NotBlank(message = "신고 사유를 골라 주세요.")
        String reason
) {
}
