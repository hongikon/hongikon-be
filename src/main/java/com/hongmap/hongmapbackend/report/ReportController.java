package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.report.dto.ReportCreateRequest;
import com.hongmap.hongmapbackend.report.dto.ReportFlagRequest;
import com.hongmap.hongmapbackend.report.dto.ReportFlagResponse;
import com.hongmap.hongmapbackend.report.dto.ReportListResponse;
import com.hongmap.hongmapbackend.report.dto.ReportResponse;
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

/**
 * 석훈님 "실시간 제보 기능 — API 스펙" 확정본(2026-08-10) 기준.
 * GET /reports?live=true 는 SecurityConfig의 permitAll()에 등록 필요.
 */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/reports")
    public ResponseEntity<ReportResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ReportCreateRequest request
    ) {
        ReportResponse response = reportService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/reports")
    public ReportListResponse getLiveReports(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false, defaultValue = "false") boolean live,
            @RequestParam(required = false) Long buildingId
    ) {
        // live=false 케이스(전체 조회)는 현재 스펙에 없어 live 목록으로 통일.
        // 추후 필요 시 reportService.getAllReports(...) 분기 추가.
        return reportService.getLiveReports(userId, buildingId);
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        reportService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reports/{id}/flags")
    public ResponseEntity<ReportFlagResponse> flag(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ReportFlagRequest request
    ) {
        ReportFlagResponse response = reportService.flag(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
