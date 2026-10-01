package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 크롤러가 새로 저장한 소식을 구독자 기기로 푸시한다(Expo Push API).
 *
 * 한 번의 크롤링에서 모은 새 소식 전체의 메시지를 만든 뒤 {@value ExpoPushClient#MAX_BATCH_SIZE}개씩 묶어 보낸다
 * — 소식마다 API를 부르지 않아 요청 수가 적고, 크롤링이 다 끝난 뒤에 한 번 돌기 때문에 게시판 수집을 늦추지 않는다.
 * 대상 조회·배치 발송 실패는 로그만 남기고 다음으로 넘어간다(크롤링 결과에 영향 없음).
 *
 * 영수증(receipt) 조회는 하지 않는다 — 발송 직후 응답(ticket)에서 바로 알 수 있는 DeviceNotRegistered만 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsPushDispatcher {

    static final String DATA_TYPE_NEWS = "NEWS";
    private static final String DEFAULT_SOURCE_LABEL = "홍익대학교";

    private final UserDeviceRepository userDeviceRepository;
    private final ExpoPushClient expoPushClient;
    private final PushProperties properties;

    /** 새 소식들을 푸시하고 Expo가 받아들인(status=ok) 메시지 수를 돌려준다. 예외를 던지지 않는다. */
    public int dispatch(List<News> newsList) {
        if (!properties.isEnabled() || newsList == null || newsList.isEmpty()) {
            return 0;
        }

        List<ExpoPushMessage> messages = buildMessages(newsList);
        if (messages.isEmpty()) {
            return 0;
        }

        int accepted = 0;
        Set<String> unregisteredTokens = new LinkedHashSet<>();
        for (int from = 0; from < messages.size(); from += ExpoPushClient.MAX_BATCH_SIZE) {
            List<ExpoPushMessage> batch = messages.subList(from, Math.min(from + ExpoPushClient.MAX_BATCH_SIZE, messages.size()));
            try {
                List<ExpoPushTicket> tickets = expoPushClient.send(batch);
                for (int i = 0; i < tickets.size() && i < batch.size(); i++) {
                    ExpoPushTicket ticket = tickets.get(i);
                    if ("ok".equals(ticket.status())) {
                        accepted++;
                    } else if (ticket.isDeviceNotRegistered()) {
                        unregisteredTokens.add(batch.get(i).to());
                    } else {
                        log.warn("Expo 푸시 거부: to={}, message={}, details={}", batch.get(i).to(), ticket.message(), ticket.details());
                    }
                }
            } catch (Exception e) {
                log.warn("Expo 푸시 배치 발송 실패 ({}건): {}", batch.size(), e.getMessage());
            }
        }

        deactivate(unregisteredTokens);
        log.info("새 소식 푸시: 소식 {}건, 메시지 {}건 중 {}건 접수, 비활성화 기기 {}대",
                newsList.size(), messages.size(), accepted, unregisteredTokens.size());
        return accepted;
    }

    /** 소식 하나의 푸시 대상 기기. 매칭 기준은 {@link UserDeviceRepository#findPushTargets} 참고. */
    public List<UserDevice> findTargets(News news) {
        Long departmentId = news.getDepartment() != null ? news.getDepartment().getId() : null;
        return userDeviceRepository.findPushTargets(TokenType.EXPO, departmentId, news.getCategory(), news.getTitle());
    }

    private List<ExpoPushMessage> buildMessages(List<News> newsList) {
        LocalDateTime cutoff = LocalDate.now().minusDays(properties.getNewsMaxAgeDays()).atStartOfDay();
        List<ExpoPushMessage> messages = new ArrayList<>();

        for (News news : newsList) {
            if (news.getPublishedAt() != null && news.getPublishedAt().isBefore(cutoff)) {
                log.debug("오래된 소식이라 푸시 생략: id={}, publishedAt={}", news.getId(), news.getPublishedAt());
                continue;
            }
            try {
                // push_token은 UNIQUE지만, 소식 하나에 같은 토큰이 두 번 들어가지 않도록 한 번 더 거른다.
                Set<String> tokens = new LinkedHashSet<>();
                for (UserDevice device : findTargets(news)) {
                    tokens.add(device.getPushToken());
                }
                for (String token : tokens) {
                    messages.add(toMessage(news, token));
                }
            } catch (Exception e) {
                log.warn("푸시 대상 조회 실패 (newsId={}): {}", news.getId(), e.getMessage());
            }
        }
        return messages;
    }

    private ExpoPushMessage toMessage(News news, String token) {
        return ExpoPushMessage.of(
                token,
                sourceLabel(news),
                news.getTitle(),
                Map.of("type", DATA_TYPE_NEWS, "newsId", news.getId())
        );
    }

    /** 알림 제목 — 어느 게시판 글인지. 학과명 → 게시판 출처(학사·장학 등) → 기본값 순. */
    private String sourceLabel(News news) {
        if (news.getDepartment() != null && news.getDepartment().getName() != null) {
            return news.getDepartment().getName();
        }
        if (news.getSourceId() != null && !news.getSourceId().isBlank()) {
            return news.getSourceId() + " 공지";
        }
        return DEFAULT_SOURCE_LABEL;
    }

    private void deactivate(Set<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        try {
            int count = userDeviceRepository.deactivateByPushTokens(tokens);
            log.info("DeviceNotRegistered 기기 비활성화: {}대", count);
        } catch (Exception e) {
            log.warn("푸시 기기 비활성화 실패 ({}개 토큰): {}", tokens.size(), e.getMessage());
        }
    }
}
