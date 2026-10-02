package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.common.dto.PageResponse;
import com.hongmap.hongmapbackend.report.dto.MyReportCountResponse;
import com.hongmap.hongmapbackend.report.dto.MyReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 제보 내역. 로그인 필수(SecurityConfig 의 anyRequest().authenticated()). 삭제는 기존 DELETE /reports/{id} 를 쓴다. */
@RestController
@RequiredArgsConstructor
public class MyReportController {

    private final MyReportService myReportService;

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "내 제보 내역",
            description = "내가 올린 제보를 최신 등록순으로 돌려줍니다(관리자가 삭제한 것도 DELETED 로 포함). "
                    + "status 는 저장된 검토 상태, displayStatus 는 화면용 상태입니다: "
                    + "PENDING(승인 대기) / SCHEDULED(승인됨·시작 전) / ACTIVE(지도에 표시 중) / ENDED(기간 종료) / "
                    + "REJECTED(반려) / HIDDEN(숨김) / DELETED(삭제됨). "
                    + "moderationNote 는 REJECTED·HIDDEN 일 때만 채워지는 작성자용 사유입니다. "
                    + "page 는 0부터, size 는 기본 20·최대 50. 삭제는 DELETE /reports/{id}.")
    @GetMapping("/users/me/reports")
    public PageResponse<MyReportResponse> getMine(
            @AuthenticationPrincipal Long userId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return myReportService.getMine(userId, pageable);
    }

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "내 제보 개수", description = "설정 화면 배지용 — 전체 개수와 승인 대기(PENDING) 개수.")
    @GetMapping("/users/me/reports/count")
    public MyReportCountResponse count(@AuthenticationPrincipal Long userId) {
        return myReportService.count(userId);
    }
}
