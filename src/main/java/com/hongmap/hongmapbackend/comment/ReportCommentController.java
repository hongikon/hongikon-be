package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.comment.dto.CommentCreateRequest;
import com.hongmap.hongmapbackend.comment.dto.CommentFlagRequest;
import com.hongmap.hongmapbackend.comment.dto.CommentFlagResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentResponse;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 제보 댓글. 목록은 게스트도, 쓰기·삭제·신고는 로그인 필요(SecurityConfig). */
@Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
@RestController
@RequiredArgsConstructor
public class ReportCommentController {

    private final ReportCommentService commentService;

    @Operation(summary = "제보 댓글 목록", description = "지도에 공개된 제보의 공개 댓글. 기본 오래된 순(order=latest 면 최신 순), "
            + "page 0부터, size 기본 20·최대 50. totalElements 가 댓글 수. 공개되지 않은 제보는 404.")
    @GetMapping("/reports/{reportId}/comments")
    public PageResponse<CommentResponse> list(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long reportId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String order) {
        return commentService.list(userId, reportId, page, size, order);
    }

    @Operation(summary = "제보 댓글 쓰기", description = "앞뒤 공백을 지운 뒤 1~200자. 1분 5개·하루 50개를 넘으면 429, 끝난 제보는 409.")
    @PostMapping("/reports/{reportId}/comments")
    public ResponseEntity<CommentResponse> create(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long reportId,
                                                  @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(userId, reportId, request.content()));
    }

    @Operation(summary = "내 댓글 지우기")
    @DeleteMapping("/reports/{reportId}/comments/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long reportId,
                                       @PathVariable Long commentId) {
        commentService.deleteOwn(userId, reportId, commentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "댓글 신고", description = "reason: FALSE_INFO / SPAM / INAPPROPRIATE / PRIVACY / ETC. 같은 댓글 중복 신고 409, "
            + "내 댓글 400. 신고가 3개 쌓이면 자동으로 숨겨진다(hidden=true).")
    @PostMapping("/reports/{reportId}/comments/{commentId}/flags")
    public ResponseEntity<CommentFlagResponse> flag(@AuthenticationPrincipal Long userId,
                                                    @PathVariable Long reportId,
                                                    @PathVariable Long commentId,
                                                    @Valid @RequestBody CommentFlagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.flag(userId, reportId, commentId, request.reason()));
    }
}
