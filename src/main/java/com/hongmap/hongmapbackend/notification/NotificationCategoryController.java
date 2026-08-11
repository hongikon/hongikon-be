package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.notification.dto.NotificationCategoryListResponse;
import com.hongmap.hongmapbackend.notification.dto.NotificationCategoryResponse;
import com.hongmap.hongmapbackend.notification.dto.NotificationCategoryToggleRequest;
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

    @GetMapping("/users/me/notification-categories")
    public NotificationCategoryListResponse getMyCategories(@AuthenticationPrincipal Long userId) {
        return notificationCategoryService.getUserCategories(userId);
    }

    @PatchMapping("/users/me/notification-categories/{category}")
    public NotificationCategoryResponse toggle(
            @AuthenticationPrincipal Long userId,
            @PathVariable String category,
            @Valid @RequestBody NotificationCategoryToggleRequest request
    ) {
        return notificationCategoryService.toggle(userId, category, request.enabled());
    }
}
