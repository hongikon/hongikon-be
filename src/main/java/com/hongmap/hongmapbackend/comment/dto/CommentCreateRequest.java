package com.hongmap.hongmapbackend.comment.dto;

import jakarta.validation.constraints.NotNull;

/** 길이·공백 검사는 서비스에서(앞뒤 공백을 지운 뒤 1~200자). */
public record CommentCreateRequest(
        @NotNull(message = "댓글 내용을 입력해 주세요.")
        String content
) {
}
