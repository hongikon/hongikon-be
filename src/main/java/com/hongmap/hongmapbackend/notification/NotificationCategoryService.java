package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.notification.dto.NotificationCategoryListResponse;
import com.hongmap.hongmapbackend.notification.dto.NotificationCategoryResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * [가정 A, 트래커 확인중] 카테고리 7종: 공지/장학/행사/수강/시설/취업/상담.
 * 석훈님 확인되면 이 CATEGORIES 목록만 수정하면 됨 — 나머지 로직은 값에 무관.
 */
@Service
@RequiredArgsConstructor
public class NotificationCategoryService {

    private static final List<String> CATEGORIES =
            List.of("공지", "장학", "행사", "수강", "시설", "취업", "상담");

    private final NotificationCategoryRepository notificationCategoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public NotificationCategoryListResponse getUserCategories(Long userId) {
        Map<String, Boolean> saved = notificationCategoryRepository.findByUser_Id(userId).stream()
                .collect(Collectors.toMap(NotificationCategory::getCategory, NotificationCategory::getEnabled));

        // 저장된 적 없는 카테고리는 기본값(true)으로 채워서 응답 — 유저가 앱을 처음 열어도 7개가 다 보이게.
        List<NotificationCategoryResponse> categories = CATEGORIES.stream()
                .map(category -> new NotificationCategoryResponse(category, saved.getOrDefault(category, true)))
                .toList();

        return new NotificationCategoryListResponse(categories);
    }

    @Transactional
    public NotificationCategoryResponse toggle(Long userId, String category, boolean enabled) {
        if (!CATEGORIES.contains(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다: " + category);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        // NotificationCategory는 @Setter가 없는 불변 지향 엔티티라, 값이 바뀌면 새로 만들어
        // save()하는 방식(upsert)으로 처리. PK가 (user_id, category) 복합키라 JPA가 자동으로
        // update/insert를 구분해줌.
        NotificationCategory toSave = NotificationCategory.builder()
                .user(user)
                .category(category)
                .enabled(enabled)
                .build();
        notificationCategoryRepository.save(toSave);

        return new NotificationCategoryResponse(category, enabled);
    }
}
