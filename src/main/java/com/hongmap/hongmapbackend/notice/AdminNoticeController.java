package com.hongmap.hongmapbackend.notice;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.notice.dto.AdminNoticeListResponse;
import com.hongmap.hongmapbackend.notice.dto.AdminNoticeRequest;
import com.hongmap.hongmapbackend.notice.dto.AdminNoticeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 공지사항 관리. ADMIN 전용 — SecurityConfig 의 /admin/** 규칙, 접속 기록은 AdminAuditInterceptor.
 * 쓰기가 성공하면 GET /notices 가 바로 새 값을 준다(게시 시각이 지난 것만).
 */
@Slf4j
@Tag(name = SwaggerConfig.TAG_ADMIN)
@RestController
@RequestMapping("/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final AppNoticeService service;

    @Operation(summary = "공지 목록", description = "예약(게시 전) 포함 전부, 게시 시각 늦은 순. status 는 SCHEDULED·PUBLISHED.")
    @GetMapping
    public AdminNoticeListResponse notices() {
        return service.all();
    }

    @Operation(summary = "공지 추가", description = "publishAt 부터 앱에 보인다(지난 시각이면 바로). "
            + "popupUntil 이 있으면 그때까지 앱을 켤 때 popupText 를 팝업으로(게시 후 31일까지).")
    @PostMapping
    public ResponseEntity<AdminNoticeResponse> create(@AuthenticationPrincipal Long adminId,
                                                      @Valid @RequestBody AdminNoticeRequest request) {
        AdminNoticeResponse saved = service.create(request);
        log.info("공지 추가: adminId={}, id={}", adminId, saved.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "공지 수정", description = "본문 내용으로 통째로 바꾼다.")
    @PutMapping("/{id}")
    public AdminNoticeResponse update(@AuthenticationPrincipal Long adminId, @PathVariable Long id,
                                      @Valid @RequestBody AdminNoticeRequest request) {
        AdminNoticeResponse saved = service.update(id, request);
        log.info("공지 수정: adminId={}, id={}", adminId, id);
        return saved;
    }

    @Operation(summary = "공지 삭제", description = "앱 목록에서도 바로 빠진다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        service.delete(id);
        log.info("공지 삭제: adminId={}, id={}", adminId, id);
        return ResponseEntity.noContent().build();
    }
}
