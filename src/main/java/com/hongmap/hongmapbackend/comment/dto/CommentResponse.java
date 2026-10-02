package com.hongmap.hongmapbackend.comment.dto;

import com.hongmap.hongmapbackend.comment.ReportComment;

import java.time.LocalDateTime;

/**
 * 공개 댓글 한 개. 작성자는 표시 이름(앱 닉네임 또는 가린 로그인 닉네임)과 불투명한 authorKey 만 싣는다 — users.id 는 절대 싣지 않는다.
 *
 * @param authorKey 앱의 "이 사용자 숨기기"용(제보의 authorKey 와 같은 값). 서버 키가 없으면 null
 * @param isMine    요청한 사람이 쓴 댓글인지(삭제 버튼 표시용). 비로그인이면 항상 false
 */
public record CommentResponse(
        Long id,
        Long reportId,
        String content,
        String authorDisplayName,
        String authorKey,
        boolean isMine,
        LocalDateTime createdAt
) {
    public static CommentResponse of(ReportComment comment, Long requesterId, String authorKey) {
        Long authorId = comment.getUser().getId();
        return new CommentResponse(
                comment.getId(),
                comment.getReport().getId(),
                comment.getContent(),
                comment.getUser().getDisplayName(),
                authorKey,
                requesterId != null && requesterId.equals(authorId),
                comment.getCreatedAt());
    }
}
