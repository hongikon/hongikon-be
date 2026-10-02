package com.hongmap.hongmapbackend.comment.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 댓글 목록 한 페이지. 페이지 단위는 최상위 댓글(답글은 각 항목의 replies 에 붙는다).
 *
 * @param totalElements 최상위 댓글 수(자리 표시 포함) — 페이지 계산용
 * @param commentCount  이 제보의 공개 댓글 수(답글 포함) — 화면의 "댓글 N"
 */
public record CommentListResponse(
        List<CommentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        long commentCount
) {
    public static CommentListResponse of(Page<?> page, List<CommentResponse> content, long commentCount) {
        return new CommentListResponse(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext(), commentCount);
    }
}
