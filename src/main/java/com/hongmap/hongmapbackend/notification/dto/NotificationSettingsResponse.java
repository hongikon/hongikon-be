package com.hongmap.hongmapbackend.notification.dto;

import com.hongmap.hongmapbackend.notification.NewReportScope;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * @param reportStatus    내 제보 결과(승인·반려) 알림
 * @param newReports      캠퍼스 새 제보 알림
 * @param newReportsScope 새 제보 알림 범위 — CAMPUS(전체) 또는 KEYWORDS(내 제보 키워드만)
 * @param adminAlerts     관리자 알림(새 제보 승인 대기·새 문의·신고 자동 숨김). 관리자에게만 의미가 있다(기본 켜짐)
 * @param partnerAdConsentAt 제휴 혜택 알림(광고성 정보) 수신 동의 시각 — ISO-8601 KST(예: 2026-10-26T14:03:00+09:00).
 *                           동의하지 않았으면 null
 */
public record NotificationSettingsResponse(
        boolean reportStatus,
        boolean newReports,
        NewReportScope newReportsScope,
        boolean adminAlerts,
        String partnerAdConsentAt
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static NotificationSettingsResponse defaults() {
        return new NotificationSettingsResponse(
                UserNotificationSetting.DEFAULT_REPORT_STATUS_ENABLED,
                UserNotificationSetting.DEFAULT_NEW_REPORTS_ENABLED,
                NewReportScope.CAMPUS,
                UserNotificationSetting.DEFAULT_ADMIN_ALERTS_ENABLED,
                null);
    }

    public static NotificationSettingsResponse from(UserNotificationSetting setting) {
        return new NotificationSettingsResponse(
                setting.isReportStatusEnabled(), setting.isNewReportsEnabled(), setting.getNewReportsScope(),
                setting.isAdminAlertsEnabled(), kst(setting.getPartnerAdConsentAt()));
    }

    /** DB 의 UTC 시각 → KST 오프셋 ISO-8601 문자열. */
    static String kst(LocalDateTime utc) {
        if (utc == null) {
            return null;
        }
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).toOffsetDateTime()
                .truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
