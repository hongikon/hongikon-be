package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionCreateRequest;
import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionListResponse;
import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KeywordSubscriptionController {

    private final KeywordSubscriptionService keywordSubscriptionService;

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "내 키워드 구독 목록 조회", description = "로그인한 사용자가 구독 중인 알림 키워드 목록을 조회합니다.")
    @GetMapping("/users/me/keyword-subscriptions")
    public KeywordSubscriptionListResponse getMyKeywords(@AuthenticationPrincipal Long userId) {
        return keywordSubscriptionService.getUserKeywords(userId);
    }

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "키워드 구독 추가", description = "특정 키워드를 포함한 공지사항 알림을 받도록 구독을 추가합니다.")
    @PostMapping("/users/me/keyword-subscriptions")
    public ResponseEntity<KeywordSubscriptionResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody KeywordSubscriptionCreateRequest request
    ) {
        var response = keywordSubscriptionService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "키워드 구독 삭제", description = "등록해둔 키워드 알림 구독을 삭제합니다.")
    @DeleteMapping("/users/me/keyword-subscriptions/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        keywordSubscriptionService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
