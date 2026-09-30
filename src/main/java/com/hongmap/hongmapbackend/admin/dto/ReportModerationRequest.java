package com.hongmap.hongmapbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param status 목표 상태: ACTIVE(승인·재공개) / REJECTED(반려) / HIDDEN(숨김) / DELETED(삭제)
 * @param note   선택. 반려 사유 등 관리자 메모
 */
public record ReportModerationRequest(
        @NotBlank String status,
        @Size(max = 200) String note
) {
}
