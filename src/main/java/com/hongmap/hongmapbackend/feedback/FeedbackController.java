package com.hongmap.hongmapbackend.feedback;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.feedback.dto.FeedbackCreateRequest;
import com.hongmap.hongmapbackend.feedback.dto.FeedbackListResponse;
import com.hongmap.hongmapbackend.feedback.dto.FeedbackResponse;
import com.hongmap.hongmapbackend.feedback.dto.FeedbackStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "문의하기", description = "로그인 선택. 토큰이 있으면 작성자로 연결된다.")
    @PostMapping("/feedback")
    public ResponseEntity<Void> create(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody FeedbackCreateRequest request) {
        feedbackService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Tag(name = SwaggerConfig.TAG_ADMIN)
    @Operation(summary = "문의 목록(관리자)", description = "status: OPEN(기본)/RESOLVED/ALL. 최신순 최대 200건.")
    @GetMapping("/admin/feedback")
    public FeedbackListResponse list(@RequestParam(required = false) String status) {
        return feedbackService.list(status);
    }

    @Tag(name = SwaggerConfig.TAG_ADMIN)
    @Operation(summary = "문의 처리 상태 변경(관리자)", description = "OPEN / RESOLVED")
    @PatchMapping("/admin/feedback/{id}")
    public FeedbackResponse changeStatus(@PathVariable Long id, @Valid @RequestBody FeedbackStatusRequest request) {
        return feedbackService.changeStatus(id, request.status());
    }
}
