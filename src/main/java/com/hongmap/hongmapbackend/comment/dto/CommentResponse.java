package com.hongmap.hongmapbackend.comment.dto;

import com.hongmap.hongmapbackend.comment.ReportComment;
import com.hongmap.hongmapbackend.comment.ReportCommentStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공개 댓글 한 개. 작성자는 표시 이름(앱 닉네임 또는 가린 로그인 닉네임)과 불투명한 authorKey 만 싣는다 — users.id 는 절대 싣지 않는다.
 *
 * @param parentId    답글이면 최상위 댓글 id, 최상위 댓글이면 null
 * @param placeholder 지워졌거나(DELETED) 숨겨졌지만(HIDDEN) 공개 답글이 남아 자리만 보여 주는 최상위 댓글이면 그 상태 —
 *                    이때 content·작성자는 null 이다. 보통 댓글은 null
 * @param authorKey   앱의 "이 사용자 숨기기"용(제보의 authorKey 와 같은 값). 서버 키가 없으면 null
 * @param isMine      요청한 사람이 쓴 댓글인지(삭제 버튼 표시용). 비로그인이면 항상 false
 * @param replies     최상위 댓글에만: 공개 답글 앞쪽 최대 3개(오래된 순). 답글 항목에서는 null
 * @param replyCount  최상위 댓글에만: 공개 답글 수. 답글 항목에서는 0
 * @param likeCount   👍 수(자리 표시는 0)
 * @param likedByMe   요청한 사람이 👍를 눌렀는지(비로그인이면 false)
 * @param flaggedByMe 요청한 사람이 이 댓글을 신고했는지(비로그인·자리 표시면 false). 앱이 다시 열어도 "신고함"을 유지하게
 */
public record CommentResponse(
        Long id,
        Long reportId,
        Long parentId,
        String content,
        String placeholder,
        String authorDisplayName,
        String authorKey,
        boolean isMine,
        LocalDateTime createdAt,
        List<CommentResponse> replies,
        long replyCount,
        long likeCount,
        boolean likedByMe,
        boolean flaggedByMe
) {
    public static CommentResponse of(ReportComment comment, Long requesterId, String authorKey,
                                     List<CommentResponse> replies, long replyCount) {
        if (comment.getStatus() != ReportCommentStatus.VISIBLE) {
            // 자리 표시: 내용·작성자를 싣지 않는다.
            return new CommentResponse(comment.getId(), comment.getReport().getId(), comment.getParentId(), null,
                    comment.getStatus().name(), null, null, false, comment.getCreatedAt(), replies, replyCount, 0, false, false);
        }
        Long authorId = comment.getUser().getId();
        return new CommentResponse(
                comment.getId(),
                comment.getReport().getId(),
                comment.getParentId(),
                comment.getContent(),
                null,
                comment.getUser().getDisplayName(),
                authorKey,
                requesterId != null && requesterId.equals(authorId),
                comment.getCreatedAt(),
                replies,
                replyCount,
                0,
                false,
                false);
    }

    /** 같은 내용에 👍 값·내 신고 여부와 답글 목록을 바꾼 사본. 자리 표시는 늘 0·false. */
    public CommentResponse withViewerState(long likeCount, boolean likedByMe, boolean flaggedByMe,
                                           List<CommentResponse> replies) {
        boolean placeholderRow = placeholder != null;
        return new CommentResponse(id, reportId, parentId, content, placeholder, authorDisplayName, authorKey, isMine,
                createdAt, replies, replyCount, placeholderRow ? 0 : likeCount, !placeholderRow && likedByMe,
                !placeholderRow && flaggedByMe);
    }
}
