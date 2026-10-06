package com.hongmap.hongmapbackend.notification.dto;

import com.hongmap.hongmapbackend.notification.NewReportScope;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;

/**
 * @param reportStatus    내 제보 결과(승인·반려) 알림
 * @param newReports      캠퍼스 새 제보 알림
 * @param newReportsScope 새 제보 알림 범위 — CAMPUS(전체) 또는 KEYWORDS(내 제보 키워드만)
 * @param adminAlerts     관리자 알림(새 제보 승인 대기·새 문의·신고 자동 숨김). 관리자에게만 의미가 있다(기본 켜짐)
 */
public record NotificationSettingsResponse(
        boolean reportStatus,
        boolean newReports,
        NewReportScope newReportsScope,
        boolean adminAlerts
) {
    public static NotificationSettingsResponse defaults() {
        return new NotificationSettingsResponse(
                UserNotificationSetting.DEFAULT_REPORT_STATUS_ENABLED,
                UserNotificationSetting.DEFAULT_NEW_REPORTS_ENABLED,
                NewReportScope.CAMPUS,
                UserNotificationSetting.DEFAULT_ADMIN_ALERTS_ENABLED);
    }

    public static NotificationSettingsResponse from(UserNotificationSetting setting) {
        return new NotificationSettingsResponse(
                setting.isReportStatusEnabled(), setting.isNewReportsEnabled(), setting.getNewReportsScope(),
                setting.isAdminAlertsEnabled());
    }
}
