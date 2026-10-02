package com.hongmap.hongmapbackend.comment;

/**
 * 새 댓글이 커밋된 뒤 제보 작성자에게 푸시하려고 발행한다(ReportCommentPushDispatcher).
 * 비동기 스레드에서 지연 로딩을 하지 않도록 필요한 값을 미리 담는다.
 */
public record ReportCommentCreatedEvent(
        Long reportId,
        Long reportAuthorId,
        Long commenterId,
        String reportTitle,
        String content
) {
}
