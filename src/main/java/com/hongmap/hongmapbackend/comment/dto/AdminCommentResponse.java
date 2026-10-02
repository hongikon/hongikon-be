package com.hongmap.hongmapbackend.comment.dto;

import com.hongmap.hongmapbackend.comment.ReportComment;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 관리자 댓글 목록의 한 줄. 공개 응답과 달리 상태·작성자 id·로그인 닉네임 원문·신고 수(사유별)를 싣는다.
 */
public record AdminCommentResponse(
        Long id,
        Long reportId,
        /** 답글이면 최상위 댓글 id */
        Long parentId,
        String content,
        String status,
        Long authorId,
        String authorNickname,
        String authorDisplayName,
        long flagCount,
        Map<String, Long> flagReasons,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
    public static AdminCommentResponse of(ReportComment comment, Map<String, Long> flagReasons) {
        Map<String, Long> reasons = flagReasons == null ? Map.of() : flagReasons;
        return new AdminCommentResponse(
                comment.getId(),
                comment.getReport().getId(),
                comment.getParentId(),
                comment.getContent(),
                comment.getStatus().name(),
                comment.getUser().getId(),
                comment.getUser().getNickname(),
                comment.getUser().getDisplayName(),
                reasons.values().stream().mapToLong(Long::longValue).sum(),
                reasons,
                comment.getCreatedAt(),
                comment.getReviewedAt());
    }
}
