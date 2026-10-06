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
 * 제보 전용 키워드(/users/me/report-keywords). 검증·정규화·개수 제한·오류 코드는 소식 키워드(KeywordSubscriptionService)와 같다:
 * 앞뒤 공백 제거, 최대 30자(@Size), 유저당 30개, 같은 키워드(대소문자 무시) 409.
 * 다른 유저의 키워드는 조회·삭제할 수 없다 — 삭제는 남의 id 도 "없음"(404)으로 돌려 존재 여부를 드러내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ReportKeywordSubscriptionService {

    static final int MAX_KEYWORDS_PER_USER = 30;
    static final String DUPLICATE_MESSAGE = "이미 등록된 키워드입니다.";

    private final ReportKeywordSubscriptionRepository repository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public KeywordSubscriptionListResponse getUserKeywords(Long userId) {
        var keywords = repository.findByUser_IdOrderByIdAsc(userId).stream()
                .map(ReportKeywordSubscriptionService::toResponse)
                .toList();
        return new KeywordSubscriptionListResponse(keywords);
    }

    @Transactional
    public KeywordSubscriptionResponse create(Long userId, KeywordSubscriptionCreateRequest request) {
        String keyword = request.keyword().trim();
        if (keyword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "키워드를 입력해 주세요.");
        }
        if (repository.existsByUser_IdAndKeywordIgnoreCase(userId, keyword)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
        }
        if (repository.countByUser_Id(userId) >= MAX_KEYWORDS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "키워드는 최대 " + MAX_KEYWORDS_PER_USER + "개까지 등록할 수 있어요.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        ReportKeywordSubscription saved;
        try {
            saved = repository.saveAndFlush(ReportKeywordSubscription.builder().user(user).keyword(keyword).build());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
        }
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        ReportKeywordSubscription keyword = repository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 키워드입니다."));
        repository.delete(keyword);
    }

    private static KeywordSubscriptionResponse toResponse(ReportKeywordSubscription k) {
        return KeywordSubscriptionResponse.builder().id(k.getId()).keyword(k.getKeyword()).build();
    }
}
