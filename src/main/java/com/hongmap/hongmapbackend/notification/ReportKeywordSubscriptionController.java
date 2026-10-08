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

/** 제보 전용 키워드 알림. 소식 키워드(/users/me/keyword-subscriptions)와 별개. 전부 로그인 필요. */
@RestController
@RequiredArgsConstructor
public class ReportKeywordSubscriptionController {

    private final ReportKeywordSubscriptionService service;

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "내 제보 키워드 목록 조회", description = "새 제보 알림에 쓰는 제보 전용 키워드를 등록 순서대로 돌려줍니다.")
    @GetMapping("/users/me/report-keywords")
    public KeywordSubscriptionListResponse getMine(@AuthenticationPrincipal Long userId) {
        return service.getUserKeywords(userId);
    }

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "제보 키워드 추가",
            description = "제목·본문·장소·분류·건물명에 이 키워드가 들어간 새 제보가 올라오면(대소문자·공백 무시) "
                    + "새 제보 알림이 켜져 있을 때 빈도 제한 없이 알려 줍니다. 최대 30자, 30개. 중복이면 409.")
    @PostMapping("/users/me/report-keywords")
    public ResponseEntity<KeywordSubscriptionResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody KeywordSubscriptionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(userId, request));
    }

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "제보 키워드 삭제", description = "본인 키워드만 지울 수 있습니다(그 밖에는 404).")
    @DeleteMapping("/users/me/report-keywords/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
