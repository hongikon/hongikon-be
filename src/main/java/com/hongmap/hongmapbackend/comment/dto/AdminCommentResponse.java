package com.hongmap.hongmapbackend.comment.dto;

import com.hongmap.hongmapbackend.comment.ReportComment;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 관리자 댓글 목록의 한 줄. 공개 응답과 달리 상태·작성자 id·신고 수(사유별)를 싣는다.
 * 작성자는 앱에 보이는 이름(authorDisplayName)과 공개 회원 번호(authorMemberCode)로만 가리킨다 — 로그인(카카오/Apple)
 * 닉네임 원문은 싣지 않는다(관리자 제보 응답과 같은 규칙, 원문은 GET /admin/users/{id}/login-name 으로만).
 */
public record AdminCommentResponse(
        Long id,
        Long reportId,
        /** 답글이면 최상위 댓글 id */
        Long parentId,
        String content,
        String status,
        Long authorId,
        /** 구버전 관리자 화면 호환용 — authorDisplayName 과 같은 값(로그인 닉네임 원문 아님). */
        String authorNickname,
        String authorDisplayName,
        long flagCount,
        Map<String, Long> flagReasons,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        /** 작성자 공개 회원 번호(K7Q2M9XA4D). 회원 조회(q=)에 그대로 넣어 찾는다. */
        String authorMemberCode
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
                comment.getUser().getDisplayName(),
                comment.getUser().getDisplayName(),
                reasons.values().stream().mapToLong(Long::longValue).sum(),
                reasons,
                comment.getCreatedAt(),
                comment.getReviewedAt(),
                comment.getUser().getMemberCode());
    }
}
