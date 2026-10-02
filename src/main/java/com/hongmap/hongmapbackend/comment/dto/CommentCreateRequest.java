package com.hongmap.hongmapbackend.comment.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 길이·공백 검사는 서비스에서(앞뒤 공백을 지운 뒤 1~200자).
 *
 * @param parentId 답글이면 답할 댓글 id. 답글에 답하면 그 답글의 최상위 댓글에 붙는다(답글은 한 단계만). 최상위 댓글이면 null
 */
public record CommentCreateRequest(
        @NotNull(message = "댓글 내용을 입력해 주세요.")
        String content,
        Long parentId
) {
}
