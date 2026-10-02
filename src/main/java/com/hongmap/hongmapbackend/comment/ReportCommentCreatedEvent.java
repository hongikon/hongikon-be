package com.hongmap.hongmapbackend.comment;

/**
 * 새 댓글·답글이 커밋된 뒤 푸시하려고 발행한다(ReportCommentPushDispatcher).
 * 비동기 스레드에서 지연 로딩을 하지 않도록 필요한 값을 미리 담는다.
 *
 * @param parentCommentId 답글이면 최상위 댓글 id, 아니면 null
 * @param parentAuthorId  답글이면 최상위 댓글 작성자 id, 아니면 null
 */
public record ReportCommentCreatedEvent(
        Long reportId,
        Long reportAuthorId,
        Long commenterId,
        String reportTitle,
        String content,
        Long parentCommentId,
        Long parentAuthorId
) {
    public ReportCommentCreatedEvent(Long reportId, Long reportAuthorId, Long commenterId, String reportTitle, String content) {
        this(reportId, reportAuthorId, commenterId, reportTitle, content, null, null);
    }
}
