package com.hongmap.hongmapbackend.comment.dto;

import com.hongmap.hongmapbackend.comment.ReportComment;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * "신고된 댓글" 목록의 한 줄(GET /admin/comments?filter=flagged). {@link AdminCommentResponse} 와 같은 필드에
 * 제보 제목·상태와 검토 뒤 신고 수(pendingFlagCount)·마지막 신고 시각을 더한다 — 관리 화면이 같은 댓글 행 컴포넌트를 쓰게 평평하게 둔다.
 * 작성자는 앱에 보이는 이름(authorDisplayName)과 공개 회원 번호(authorMemberCode)로만 가리킨다(로그인 닉네임 원문 없음).
 * 신고자는 싣지 않는다 — 사유별 수만.
 *
 * @param flagCount        전체 신고 수(flagReasons 합)
 * @param pendingFlagCount 마지막 관리자 검토(reviewedAt) 뒤 들어온 신고 수 — 자동 숨김 기준과 같다
 * @param lastFlaggedAt    마지막 신고 시각(목록 정렬 기준)
 */
public record AdminFlaggedCommentResponse(
        Long id,
        Long reportId,
        Long parentId,
        String content,
        String status,
        Long authorId,
        String authorNickname,
        String authorDisplayName,
        long flagCount,
        Map<String, Long> flagReasons,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        String authorMemberCode,
        String reportTitle,
        String reportStatus,
        long pendingFlagCount,
        LocalDateTime lastFlaggedAt
) {
    public static AdminFlaggedCommentResponse of(ReportComment comment, Map<String, Long> flagReasons,
                                                 long pendingFlagCount, LocalDateTime lastFlaggedAt) {
        AdminCommentResponse base = AdminCommentResponse.of(comment, flagReasons);
        return new AdminFlaggedCommentResponse(
                base.id(), base.reportId(), base.parentId(), base.content(), base.status(), base.authorId(),
                base.authorNickname(), base.authorDisplayName(), base.flagCount(), base.flagReasons(),
                base.createdAt(), base.reviewedAt(), base.authorMemberCode(),
                comment.getReport().getTitle(), comment.getReport().getStatus().name(),
                pendingFlagCount, lastFlaggedAt);
    }
}
