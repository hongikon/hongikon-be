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
 * [가정 B 확인됨] 자유 키워드 알림. 제목에 키워드가 포함된 새 소식은 게시판 구독·카테고리 설정과 무관하게 푸시된다(NewsPushDispatcher).
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
