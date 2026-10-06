package com.hongmap.hongmapbackend.comment;

/**
 * 댓글이 운영 정책으로 숨겨지거나(HIDDEN) 삭제됐을 때(DELETED) 커밋 뒤 작성자에게 알리려고 발행한다
 * (CommentModerationPushDispatcher). 이용약관 제10조 — 조치 사유를 알리고 14일 이의 제기 기회를 준다.
 * 비동기 스레드에서 지연 로딩을 하지 않도록 필요한 값을 미리 담는다. 댓글 내용은 담지 않는다(푸시에 싣지 않으므로).
 *
 * @param status    바뀐 상태(HIDDEN·DELETED). VISIBLE(복원·검토 완료)은 발행하지 않는다
 * @param reason    관리자가 적은 사유(없으면 null). 자동 숨김이면 null
 * @param automatic 신고 누적 자동 숨김이면 true(관리자 조치가 아님 — 본문 문구가 다르다)
 */
public record ReportCommentModeratedEvent(
        Long commentId,
        Long reportId,
        Long authorId,
        String reportTitle,
        ReportCommentStatus status,
        String reason,
        boolean automatic
) {
}
