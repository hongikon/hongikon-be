package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
import com.hongmap.hongmapbackend.notification.dto.BoardSubscriptionListResponse;
import com.hongmap.hongmapbackend.notification.dto.BoardSubscriptionResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * 게시판 구독. 새 소식 푸시 대상은 "그 게시판(news.source_id)을 구독 + alert_enabled + 그 카테고리를 끄지 않음"인 유저다
 * (UserDeviceRepository.findPushTargets). 키워드 구독(KeywordSubscription)은 이와 별개로 추가 발송된다.
 *
 * sourceId는 크롤러가 실제로 수집하는 게시판(CrawlerBoards.ALL — 학과 게시판 + 대학공지 6개 분류)과
 * 상위 게시판을 빌려 쓰는 별칭(CrawlerBoards.SOURCE_ALIASES, 예: 데이터사이언스전공)만 허용한다.
 * 게시판이 추가·삭제되면 CrawlerBoards만 고치면 된다.
 */
@Service
@RequiredArgsConstructor
public class BoardSubscriptionService {

    /** 유저 한 명이 구독할 수 있는 게시판 수 상한. 지금 게시판이 50개 남짓이라 넉넉하게 잡았다. */
    static final int MAX_SUBSCRIPTIONS_PER_USER = 100;

    private static final Set<String> KNOWN_SOURCE_IDS = CrawlerBoards.KNOWN_SOURCE_IDS;

    private final UserBoardSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public BoardSubscriptionListResponse getUserSubscriptions(Long userId) {
        var subscriptions = subscriptionRepository.findByUser_IdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(BoardSubscriptionResponse::of)
                .toList();
        return new BoardSubscriptionListResponse(subscriptions);
    }

    /** 없으면 구독을 만들고, 있으면 알림 여부만 바꾼다. */
    @Transactional
    public BoardSubscriptionResponse upsert(Long userId, String sourceId, boolean alertEnabled) {
        validateSourceId(sourceId);

        var existing = subscriptionRepository.findByUser_IdAndSourceId(userId, sourceId);
        if (existing.isPresent()) {
            UserBoardSubscription subscription = existing.get();
            subscription.changeAlertEnabled(alertEnabled);
            return BoardSubscriptionResponse.of(subscription);
        }

        if (subscriptionRepository.countByUser_Id(userId) >= MAX_SUBSCRIPTIONS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "게시판은 최대 " + MAX_SUBSCRIPTIONS_PER_USER + "개까지 구독할 수 있습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        UserBoardSubscription saved = subscriptionRepository.saveAndFlush(
                UserBoardSubscription.builder()
                        .user(user)
                        .sourceId(sourceId)
                        .alertEnabled(alertEnabled)
                        .build()
        );
        return BoardSubscriptionResponse.of(saved);
    }

    /** 구독 해제. 구독하지 않은 게시판이어도 성공으로 본다(멱등). */
    @Transactional
    public void delete(Long userId, String sourceId) {
        validateSourceId(sourceId);
        subscriptionRepository.deleteByUserIdAndSourceId(userId, sourceId);
    }

    private void validateSourceId(String sourceId) {
        if (sourceId == null || !KNOWN_SOURCE_IDS.contains(sourceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 게시판입니다: " + sourceId);
        }
    }
}
