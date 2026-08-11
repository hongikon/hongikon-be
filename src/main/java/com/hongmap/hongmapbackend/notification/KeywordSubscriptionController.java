package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionCreateRequest;
import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionListResponse;
import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionResponse;
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

    @GetMapping("/users/me/keyword-subscriptions")
    public KeywordSubscriptionListResponse getMyKeywords(@AuthenticationPrincipal Long userId) {
        return keywordSubscriptionService.getUserKeywords(userId);
    }

    @PostMapping("/users/me/keyword-subscriptions")
    public ResponseEntity<KeywordSubscriptionResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody KeywordSubscriptionCreateRequest request
    ) {
        var response = keywordSubscriptionService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/users/me/keyword-subscriptions/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        keywordSubscriptionService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
