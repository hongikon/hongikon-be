package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.notification.dto.NotificationCategoryListResponse;
import com.hongmap.hongmapbackend.notification.dto.NotificationCategoryResponse;
import com.hongmap.hongmapbackend.notification.dto.NotificationCategoryToggleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전부 로그인 필요 (구독/알림 = README 원칙상 로그인 필요 범주).
 */
@RestController
@RequiredArgsConstructor
public class NotificationCategoryController {

    private final NotificationCategoryService notificationCategoryService;

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "내 알림 카테고리 설정 조회", description = "로그인한 사용자의 알림 카테고리별 수신 on/off 설정을 조회합니다.")
    @GetMapping("/users/me/notification-categories")
    public NotificationCategoryListResponse getMyCategories(@AuthenticationPrincipal Long userId) {
        return notificationCategoryService.getUserCategories(userId);
    }

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "알림 카테고리 수신 여부 변경", description = "특정 알림 카테고리의 수신 여부를 on/off로 변경합니다.")
    @PatchMapping("/users/me/notification-categories/{category}")
    public NotificationCategoryResponse toggle(
            @AuthenticationPrincipal Long userId,
            @PathVariable String category,
            @Valid @RequestBody NotificationCategoryToggleRequest request
    ) {
        return notificationCategoryService.toggle(userId, category, request.enabled());
    }
}
