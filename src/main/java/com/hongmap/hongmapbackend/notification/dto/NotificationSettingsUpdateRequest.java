package com.hongmap.hongmapbackend.notification.dto;

import jakarta.validation.constraints.Size;

/**
 * 부분 수정 — 보낸 필드만 바꾸고 null(생략)은 그대로 둔다.
 *
 * @param newReportsScope "CAMPUS" 또는 "KEYWORDS"(대소문자 무시, 그 외 400)
 * @param adminAlerts     관리자 알림 켜기/끄기 — 관리자가 아니어도 저장은 되지만 관리자에게만 푸시가 간다
 * @param partnerAdConsent 제휴 혜택 알림(광고성 정보) 수신 동의 — true 면 동의 시각을 지금으로(이미 동의했으면 처음 시각 유지),
 *                         false 면 동의 기록을 지운다
 */
public record NotificationSettingsUpdateRequest(
        Boolean reportStatus,
        Boolean newReports,
        @Size(max = 20, message = "newReportsScope가 너무 깁니다.")
        String newReportsScope,
        Boolean adminAlerts,
        Boolean partnerAdConsent
) {
}
