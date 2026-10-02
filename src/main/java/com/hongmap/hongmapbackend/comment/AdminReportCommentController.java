package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.comment.dto.AdminCommentListResponse;
import com.hongmap.hongmapbackend.comment.dto.AdminCommentResponse;
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

    @Operation(summary = "댓글 상태 변경", description = "VISIBLE(복원) / HIDDEN(숨김) / DELETED(삭제)")
    @PatchMapping("/admin/comments/{commentId}")
    public AdminCommentResponse moderate(@AuthenticationPrincipal Long adminId, @PathVariable Long commentId,
                                         @Valid @RequestBody CommentModerationRequest request) {
        log.info("댓글 검토: adminId={}, commentId={}, status={}", adminId, commentId, request.status());
        return commentService.moderate(commentId, request.status());
    }
}
