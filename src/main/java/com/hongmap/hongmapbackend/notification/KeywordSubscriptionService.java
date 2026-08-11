package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionCreateRequest;
import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionListResponse;
import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * [가정 B, 트래커 확인중] 자유 키워드 알림으로 해석하고 구현.
 * 만약 카테고리 구독의 다른 이름이었던 거라면, 이 클래스와 KeywordSubscription 엔티티,
 * keyword_subscriptions 테이블은 통째로 삭제 대상 — 트래커에 명시해둠.
 */
@Service
@RequiredArgsConstructor
public class KeywordSubscriptionService {

    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public KeywordSubscriptionListResponse getUserKeywords(Long userId) {
        var keywords = keywordSubscriptionRepository.findByUser_Id(userId).stream()
                .map(KeywordSubscriptionResponse::of)
                .toList();
        return new KeywordSubscriptionListResponse(keywords);
    }

    @Transactional
    public KeywordSubscriptionResponse create(Long userId, KeywordSubscriptionCreateRequest request) {
        if (keywordSubscriptionRepository.existsByUser_IdAndKeyword(userId, request.keyword())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 키워드입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        KeywordSubscription saved = keywordSubscriptionRepository.save(
                KeywordSubscription.builder()
                        .user(user)
                        .keyword(request.keyword())
                        .build()
        );

        return KeywordSubscriptionResponse.of(saved);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        KeywordSubscription keyword = keywordSubscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 구독입니다."));

        if (!keyword.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 구독만 삭제할 수 있습니다.");
        }

        keywordSubscriptionRepository.delete(keyword);
    }
}
