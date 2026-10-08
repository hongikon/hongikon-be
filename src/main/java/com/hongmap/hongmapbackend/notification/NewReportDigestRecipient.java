package com.hongmap.hongmapbackend.notification;

import java.time.LocalDateTime;

/**
 * 새 제보 다이제스트 후보 유저(UserNotificationSettingRepository.findDigestRecipients).
 * lastSentAt: 마지막 새 제보 알림(일반·다이제스트·키워드) 시각, 없으면 null. settingsUpdatedAt: 설정을 마지막으로 바꾼 시각 —
 * 받은 적 없는 유저는 이 시각(알림을 켠 때) 이후 제보만 모은다.
 */
public record NewReportDigestRecipient(Long userId, LocalDateTime lastSentAt, LocalDateTime settingsUpdatedAt) {
}
