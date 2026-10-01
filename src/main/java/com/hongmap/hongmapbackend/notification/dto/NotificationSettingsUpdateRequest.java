package com.hongmap.hongmapbackend.notification.dto;

/**
 * 부분 수정 — 보낸 필드만 바꾸고 null(생략)은 그대로 둔다.
 *
 * @param newReportsScope "CAMPUS"만 허용(그 외 400)
 */
public record NotificationSettingsUpdateRequest(
        Boolean reportStatus,
        Boolean newReports,
        String newReportsScope
) {
}
