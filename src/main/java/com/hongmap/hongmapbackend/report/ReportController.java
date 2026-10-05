package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.report.dto.ReportCreateRequest;
import com.hongmap.hongmapbackend.report.dto.ReportFlagRequest;
import com.hongmap.hongmapbackend.report.dto.ReportFlagResponse;
import com.hongmap.hongmapbackend.report.dto.ReportImageUploadRequest;
import com.hongmap.hongmapbackend.report.dto.ReportImageUploadResponse;
import com.hongmap.hongmapbackend.report.dto.ReportListResponse;
import com.hongmap.hongmapbackend.report.dto.ReportResponse;
import com.hongmap.hongmapbackend.report.image.ReportImageService;
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

/**
 * 로그인 유저가 지도의 특정 지점(건물+층)에 시간 한정 이벤트 정보를 올리는 실시간 제보 기능.
 * GET /reports는 게스트도 조회 가능, 그 외(등록/삭제/신고)는 로그인 필수.
 */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportImageService reportImageService;

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "실시간 제보 등록", description = "특정 건물/위치에 대한 실시간 제보(혼잡도, 공사 등)를 등록합니다. "
            + "사진은 POST /reports/images 로 올린 key 를 imageKeys 에 최대 3장(중복 불가) 넣습니다. "
            + "응답의 imageUrls 는 같은 순서의 보기 URL, imageUrl 은 첫 장입니다.")
    @PostMapping("/reports")
    public ResponseEntity<ReportResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ReportCreateRequest request
    ) {
        ReportResponse response = reportService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "제보 사진 업로드 URL 발급",
            description = "S3 presigned PUT URL 을 사진 1장마다 발급합니다(5분 유효, 최대 5MB, image/jpeg·image/png). "
                    + "응답의 headers 를 그대로 실어 uploadUrl 로 PUT 한 뒤, POST /reports 의 imageKeys(최대 3장) 에 key 들을 순서대로 넣습니다. "
                    + "구버전 앱의 imageKey(1장)도 계속 받습니다. "
                    + "contentLength(바이트 수)를 함께 보내면 Content-Length 가 서명되어 그 크기로만 올릴 수 있습니다. "
                    + "등록 시 서버가 사진 메타데이터(위치 등)를 지운 사본을 새 키로 저장합니다. "
                    + "저장소가 설정되지 않은 서버는 503 을 돌려줍니다.")
    @PostMapping("/reports/images")
    public ResponseEntity<ReportImageUploadResponse> issueImageUploadUrl(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ReportImageUploadRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportImageService.issueUploadUrl(userId, request.contentType(), request.contentLength()));
    }

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "실시간 제보 목록 조회", description = "지금 진행 중인(startsAt ≤ 지금 ≤ endsAt) 승인된 제보를 건물 id로 필터링해 조회합니다. "
            + "include=upcoming 이면 24시간 안에 시작할 예정 제보를 시작 시각 순으로 뒤에 덧붙입니다(항목의 startsAt 이 지금보다 뒤면 예정).")
    @GetMapping("/reports")
    public ReportListResponse getLiveReports(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false, defaultValue = "false") boolean live,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String include
    ) {
        // live=false 케이스(전체 조회)는 현재 스펙에 없어 live 목록으로 통일.
        // 추후 필요 시 reportService.getAllReports(...) 분기 추가.
        boolean includeUpcoming = include != null && include.trim().equalsIgnoreCase("upcoming");
        return reportService.getLiveReports(userId, buildingId, includeUpcoming);
    }

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "제보 삭제", description = "본인이 작성한 실시간 제보를 삭제합니다.")
    @DeleteMapping("/reports/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        reportService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "제보 신고", description = "부적절한 실시간 제보를 신고합니다.")
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
