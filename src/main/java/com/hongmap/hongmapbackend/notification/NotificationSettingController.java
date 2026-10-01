package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.notification.dto.NotificationSettingsResponse;
import com.hongmap.hongmapbackend.notification.dto.NotificationSettingsUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제보 알림 설정. 전부 로그인 필요 (구독/알림 = README 원칙상 로그인 필요 범주).
 */
@RestController
@RequiredArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "내 제보 알림 설정 조회",
            description = "reportStatus: 내 제보가 승인·반려됐을 때 알림(기본 켜짐). "
                    + "newReports: 캠퍼스에 새 제보가 올라왔을 때 알림(기본 꺼짐, 유저당 최대 30분에 한 번). "
                    + "newReportsScope: 새 제보 알림 범위(지금은 CAMPUS뿐). 저장한 적 없으면 기본값을 내려줍니다.")
    @GetMapping("/users/me/notification-settings")
    public NotificationSettingsResponse get(@AuthenticationPrincipal Long userId) {
        return notificationSettingService.get(userId);
    }

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "제보 알림 설정 변경",
            description = "보낸 필드만 바꿉니다(부분 수정). 예: {\"newReports\": true}. 바뀐 뒤의 전체 설정을 돌려줍니다.")
    @PatchMapping("/users/me/notification-settings")
    public NotificationSettingsResponse update(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody NotificationSettingsUpdateRequest request) {
        return notificationSettingService.update(userId, request);
    }
}
