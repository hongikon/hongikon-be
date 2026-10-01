package com.hongmap.hongmapbackend.notification.dto;

import com.hongmap.hongmapbackend.notification.NewReportScope;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;

/**
 * @param reportStatus    내 제보 결과(승인·반려) 알림
 * @param newReports      캠퍼스 새 제보 알림
 * @param newReportsScope 새 제보 알림 범위 — 지금은 CAMPUS뿐
 */
public record NotificationSettingsResponse(
        boolean reportStatus,
        boolean newReports,
        NewReportScope newReportsScope
) {
    public static NotificationSettingsResponse defaults() {
        return new NotificationSettingsResponse(
                UserNotificationSetting.DEFAULT_REPORT_STATUS_ENABLED,
                UserNotificationSetting.DEFAULT_NEW_REPORTS_ENABLED,
                NewReportScope.CAMPUS);
    }

    public static NotificationSettingsResponse from(UserNotificationSetting setting) {
        return new NotificationSettingsResponse(
                setting.isReportStatusEnabled(), setting.isNewReportsEnabled(), setting.getNewReportsScope());
    }
}
