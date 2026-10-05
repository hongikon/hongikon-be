package com.hongmap.hongmapbackend.comment.dto;

import jakarta.validation.constraints.NotBlank;

/** status: VISIBLE(복원) / HIDDEN(숨김) / DELETED(삭제) */
public record CommentModerationRequest(
        @NotBlank(message = "바꿀 상태를 골라 주세요.")
        String status
) {
}
