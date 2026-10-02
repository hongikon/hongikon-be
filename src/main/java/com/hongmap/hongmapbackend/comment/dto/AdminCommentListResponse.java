package com.hongmap.hongmapbackend.comment.dto;

import java.util.List;

public record AdminCommentListResponse(
        List<AdminCommentResponse> comments
) {
}
