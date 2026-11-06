package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskFeedbackAdminResponse;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 '물어보기 피드백'. /admin/** 는 관리자만(SecurityConfig). */
@RestController
@RequiredArgsConstructor
@Tag(name = SwaggerConfig.TAG_ADMIN)
public class AskFeedbackAdminController {

    private final AskFeedbackAdminService service;

    @Operation(summary = "물어보기 '해결 안 됨' 목록(관리자)", description = "status: OPEN(기본, 확인 전)/REVIEWED/ALL, "
            + "days: 며칠 전까지(기본 30, 최대 365). 서버 답(ROUTE)과 앱 안내 흐름 답(FLOW)을 최신순으로 합쳐 최대 200건. "
            + "같은 답에서 보낸 '운영진에게 물어보기' 문의(남긴 말·사진)를 붙인다.")
    @GetMapping("/admin/ask/feedback")
    public AskFeedbackAdminResponse list(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) Integer days) {
        return service.list(status, days);
    }

    @Operation(summary = "물어보기 주간 요약(관리자)", description = "days(기본 7, 최대 365) 동안 '해결 안 됨'을 같은 안내끼리, "
            + "안내를 못 고른 질문은 비슷한 것끼리 묶어 많은 순 최대 30개. 서버 안에서만 센다.")
    @GetMapping("/admin/ask/feedback/report")
    public com.hongmap.hongmapbackend.ask.dto.AskFeedbackReportResponse report(@RequestParam(required = false) Integer days) {
        return service.report(days);
    }

    public record ReviewRequest(@NotNull Boolean reviewed) {
    }

    @Operation(summary = "물어보기 피드백 확인 표시(관리자)", description = "source: ROUTE/FLOW. reviewed=false 면 되돌린다. 성공 204.")
    @PatchMapping("/admin/ask/feedback/{source}/{id}")
    public ResponseEntity<Void> review(@PathVariable String source, @PathVariable long id,
                                       @Valid @RequestBody ReviewRequest request) {
        service.review(source, id, request.reviewed());
        return ResponseEntity.noContent().build();
    }
}
