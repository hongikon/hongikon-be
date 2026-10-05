package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionCreateRequest;
import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionListResponse;
import com.hongmap.hongmapbackend.notification.dto.KeywordSubscriptionResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    /** 유저 한 명이 등록할 수 있는 키워드 수. 앱 화면은 20개까지 받는다 — 서버는 여유를 두고 남용(푸시 대상 쿼리 비용)만 막는다. */
    static final int MAX_KEYWORDS_PER_USER = 30;
    static final String DUPLICATE_MESSAGE = "이미 등록된 키워드입니다.";

    @Transactional(readOnly = true)
    public KeywordSubscriptionListResponse getUserKeywords(Long userId) {
        var keywords = keywordSubscriptionRepository.findByUser_Id(userId).stream()
                .map(KeywordSubscriptionResponse::of)
                .toList();
        return new KeywordSubscriptionListResponse(keywords);
    }

    /**
     * 앞뒤 공백을 지우고 저장한다(" 장학" 과 "장학" 이 따로 저장되던 문제). 같은 키워드(대소문자 무시)가 있으면 409 —
     * 앱(KeywordAlertsModal)이 409 를 "이미 등록한 키워드예요"로 보여 주므로 기존 응답을 그대로 둔다.
     * 동시 요청이 uq_keyword_user_keyword 에 걸린 늦은 쪽도 500 이 아니라 같은 409 로 돌려준다.
     */
    @Transactional
    public KeywordSubscriptionResponse create(Long userId, KeywordSubscriptionCreateRequest request) {
        String keyword = request.keyword().trim();
        if (keyword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "키워드를 입력해 주세요.");
        }
        if (keywordSubscriptionRepository.existsByUser_IdAndKeywordIgnoreCase(userId, keyword)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
        }
        if (keywordSubscriptionRepository.countByUser_Id(userId) >= MAX_KEYWORDS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "키워드는 최대 " + MAX_KEYWORDS_PER_USER + "개까지 등록할 수 있어요.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        KeywordSubscription saved;
        try {
            saved = keywordSubscriptionRepository.saveAndFlush(
                    KeywordSubscription.builder()
                            .user(user)
                            .keyword(keyword)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
        }

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
