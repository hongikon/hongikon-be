package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.comment.dto.AdminCommentListResponse;
import com.hongmap.hongmapbackend.comment.dto.AdminCommentResponse;
import com.hongmap.hongmapbackend.comment.dto.AdminFlaggedCommentListResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentModerationRequest;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 댓글 검토. ADMIN 전용 — SecurityConfig 의 /admin/** 규칙, 요청은 ADMIN_AUDIT 로그에 남는다. */
@Slf4j
@Tag(name = SwaggerConfig.TAG_ADMIN)
@RestController
@RequiredArgsConstructor
public class AdminReportCommentController {

    private final ReportCommentService commentService;

    @Operation(summary = "제보의 댓글 전체(숨김·삭제 포함)", description = "오래된 순. 사유별 신고 수(flagReasons)와 작성자 id·로그인 닉네임 원문을 싣는다.")
    @GetMapping("/admin/reports/{reportId}/comments")
    public AdminCommentListResponse list(@PathVariable Long reportId) {
        return commentService.adminList(reportId);
    }

    @Operation(summary = "신고된 댓글", description = "filter=flagged(기본). 마지막 검토 뒤 신고가 1건 이상인 공개·자동 숨김 댓글, "
            + "최근 신고 순 최대 200개. 항목마다 제보 제목(reportTitle)·전체/검토 뒤 신고 수·사유별 수. total 은 전체 검토 대기 수. "
            + "작성자는 표시 이름·회원 번호로만(로그인 닉네임·신고자 없음).")
    @GetMapping("/admin/comments")
    public AdminFlaggedCommentListResponse flagged(@RequestParam(defaultValue = "flagged") String filter) {
        return commentService.adminFlagged(filter);
    }

    @Operation(summary = "댓글 상태 변경", description = "VISIBLE(복원 — 이미 공개 중이면 검토 완료·유지) / HIDDEN(숨김) / DELETED(삭제). "
            + "늘 검토 시각을 남겨 그 전 신고는 더 세지 않는다. reason(선택, 200자)은 숨김·삭제 때 작성자 알림에 실린다.")
    @PatchMapping("/admin/comments/{commentId}")
    public AdminCommentResponse moderate(@AuthenticationPrincipal Long adminId, @PathVariable Long commentId,
                                         @Valid @RequestBody CommentModerationRequest request) {
        // 사유는 로그에 남기지 않는다(관리자가 개인정보를 적었을 수 있다) — 적었는지만.
        log.info("댓글 검토: adminId={}, commentId={}, status={}, reason={}", adminId, commentId, request.status(),
                request.reason() == null || request.reason().isBlank() ? "없음" : "있음");
        return commentService.moderate(commentId, request.status(), request.reason());
    }
}
