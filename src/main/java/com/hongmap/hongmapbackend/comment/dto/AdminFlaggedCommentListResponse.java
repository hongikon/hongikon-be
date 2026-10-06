package com.hongmap.hongmapbackend.comment.dto;

import java.util.List;

/**
 * 신고된 댓글 목록. comments 는 최근 신고 순 최대 200개, total 은 검토할 전체 수(대시보드 comments.flaggedPending 과 같은 값).
 */
public record AdminFlaggedCommentListResponse(
        List<AdminFlaggedCommentResponse> comments,
        long total
) {
}
