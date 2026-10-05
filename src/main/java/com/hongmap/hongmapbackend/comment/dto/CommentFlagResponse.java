package com.hongmap.hongmapbackend.comment.dto;

/** hidden: 이번 신고로 자동 숨김됐는지(앱은 목록에서 바로 뺀다). */
public record CommentFlagResponse(
        long flagCount,
        boolean hidden
) {
}
