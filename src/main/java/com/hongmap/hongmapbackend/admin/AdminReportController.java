package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.admin.dto.AdminReportFlagListResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminReportListResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminReportResponse;
import com.hongmap.hongmapbackend.admin.dto.ReportModerationRequest;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 제보 검토(승인/반려/숨김/삭제). ADMIN 전용 — SecurityConfig 의 /admin/** 규칙. */
@Slf4j
@Tag(name = SwaggerConfig.TAG_ADMIN)
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    @Operation(summary = "제보 검토 목록", description = "status: PENDING(기본)/ACTIVE/HIDDEN/REJECTED/ALL. "
            + "from·to: 등록일(한국 날짜 yyyy-MM-dd, 둘 다 포함, 선택). 기간 안에서 최신순 최대 200건.")
    @GetMapping
    public AdminReportListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return adminReportService.list(status, from, to);
    }

    @Operation(summary = "제보에 달린 신고 목록")
    @GetMapping("/{id}/flags")
    public AdminReportFlagListResponse flags(@PathVariable Long id) {
        return adminReportService.flags(id);
    }

    @Operation(summary = "제보 상태 변경", description = "ACTIVE(승인·재공개) / REJECTED(반려, 사유 필수) / HIDDEN / DELETED")
    @PatchMapping("/{id}")
    public AdminReportResponse moderate(@AuthenticationPrincipal Long adminId, @PathVariable Long id,
                                        @Valid @RequestBody ReportModerationRequest request) {
        log.info("제보 검토: adminId={}, reportId={}, status={}", adminId, id, request.status());
        return adminReportService.moderate(id, request);
    }
}
