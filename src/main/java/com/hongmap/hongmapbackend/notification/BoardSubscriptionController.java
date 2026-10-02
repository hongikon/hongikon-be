package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.notification.dto.BoardSubscriptionListResponse;
import com.hongmap.hongmapbackend.notification.dto.BoardSubscriptionResponse;
import com.hongmap.hongmapbackend.notification.dto.BoardSubscriptionUpsertRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 구독. 전부 로그인 필요 (구독/알림 = README 원칙상 로그인 필요 범주).
 * sourceId는 news.source_id와 같은 값(학과명 또는 대학공지 분류 라벨)이라 경로에 한글이 들어간다 — URL 인코딩해서 보낸다.
 */
@RestController
@RequiredArgsConstructor
public class BoardSubscriptionController {

    private final BoardSubscriptionService boardSubscriptionService;

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "내 게시판 구독 목록 조회",
            description = "로그인한 사용자가 구독 중인 게시판(학과 게시판·대학공지 분류)과 게시판별 알림 수신 여부를 조회합니다.")
    @GetMapping("/users/me/subscriptions")
    public BoardSubscriptionListResponse getMySubscriptions(@AuthenticationPrincipal Long userId) {
        return boardSubscriptionService.getUserSubscriptions(userId);
    }

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "게시판 구독 추가/알림 변경",
            description = "게시판을 구독합니다. 이미 구독 중이면 알림 수신 여부(alertEnabled)만 변경합니다. "
                    + "새 소식 푸시는 구독 중이고 alertEnabled = true인 게시판의 글 중, 알림 카테고리를 끄지 않은 글만 발송됩니다. "
                    + "sourceId는 크롤러 게시판 id(학과명 또는 학사·장학·교수학습지원·학생상담·대학혁신지원사업·학생활동)이며, "
                    + "알 수 없는 값이면 400, 구독 개수 상한(100개)을 넘으면 400입니다.")
    @PutMapping("/users/me/subscriptions/{sourceId}")
    public BoardSubscriptionResponse upsert(
            @AuthenticationPrincipal Long userId,
            @PathVariable String sourceId,
            @Valid @RequestBody BoardSubscriptionUpsertRequest request
    ) {
        return boardSubscriptionService.upsert(userId, sourceId, request.alertEnabled());
    }

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "게시판 구독 해제",
            description = "게시판 구독을 해제합니다. 구독하지 않은 게시판이어도 204로 응답합니다. 알 수 없는 sourceId는 400입니다.")
    @DeleteMapping("/users/me/subscriptions/{sourceId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable String sourceId
    ) {
        boardSubscriptionService.delete(userId, sourceId);
        return ResponseEntity.noContent().build();
    }
}
