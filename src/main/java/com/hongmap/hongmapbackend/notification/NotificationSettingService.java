package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.notification.dto.NotificationSettingsResponse;
import com.hongmap.hongmapbackend.notification.dto.NotificationSettingsUpdateRequest;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 제보 알림 설정(/users/me/notification-settings). 저장한 적 없으면 기본값(결과 알림 켜짐, 새 제보 알림 꺼짐)을 내려준다.
 * 기본값을 바꾸면 ReportPushDispatcher의 대상 기준도 같이 봐야 한다(행이 없는 유저를 어떻게 다루는지).
 */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final UserNotificationSettingRepository settingRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public NotificationSettingsResponse get(Long userId) {
        return settingRepository.findById(userId)
                .map(NotificationSettingsResponse::from)
                .orElseGet(NotificationSettingsResponse::defaults);
    }

    @Transactional
    public NotificationSettingsResponse update(Long userId, NotificationSettingsUpdateRequest request) {
        NewReportScope scope = parseScope(request.newReportsScope());
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다.");
        }

        UserNotificationSetting setting = settingRepository.findById(userId)
                .orElseGet(() -> settingRepository.save(new UserNotificationSetting(userId)));
        if (request.reportStatus() != null) {
            setting.changeReportStatusEnabled(request.reportStatus());
        }
        if (request.newReports() != null) {
            setting.changeNewReportsEnabled(request.newReports());
        }
        if (request.adminAlerts() != null) {
            setting.changeAdminAlertsEnabled(request.adminAlerts());
        }
        if (scope != null) {
            setting.changeNewReportsScope(scope);
        }
        return NotificationSettingsResponse.from(setting);
    }

    private NewReportScope parseScope(String scope) {
        if (scope == null) {
            return null;
        }
        try {
            return NewReportScope.valueOf(scope.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 알림 범위입니다: " + scope);
        }
    }
}
