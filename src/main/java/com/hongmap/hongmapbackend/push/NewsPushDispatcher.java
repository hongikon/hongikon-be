package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 크롤러가 새로 저장한 소식을 구독자 기기로 푸시한다(Expo Push API).
 *
 * 한 번의 크롤링에서 모은 새 소식 전체의 메시지를 만든 뒤 {@value ExpoPushClient#MAX_BATCH_SIZE}개씩 묶어 보낸다(ExpoPushSender)
 * — 소식마다 API를 부르지 않아 요청 수가 적고, 크롤링이 다 끝난 뒤에 한 번 돌기 때문에 게시판 수집을 늦추지 않는다.
 * 대상 조회·배치 발송 실패는 로그만 남기고 다음으로 넘어간다(크롤링 결과에 영향 없음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsPushDispatcher {

    static final String DATA_TYPE_NEWS = "NEWS";
    private static final String DEFAULT_SOURCE_LABEL = "홍익대학교";
    /** 소식 작성일(published_at)은 게시판에 적힌 한국 날짜의 0시라 "오늘"도 한국 날짜로 센다(서버 JVM 은 UTC). */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserDeviceRepository userDeviceRepository;
    private final ExpoPushSender expoPushSender;
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

        ExpoPushSender.Result result = expoPushSender.sendAll(messages);
        log.info("새 소식 푸시: 소식 {}건, 메시지 {}건 중 {}건 접수, 비활성화 기기 {}대",
                newsList.size(), messages.size(), result.accepted(), result.deactivated());
        return result.accepted();
    }

    /** 소식 하나의 푸시 대상 기기. 매칭 기준은 {@link UserDeviceRepository#findPushTargets} 참고. */
    public List<UserDevice> findTargets(News news) {
        String sourceId = news.getSourceId() != null && !news.getSourceId().isBlank() ? news.getSourceId() : null;
        return userDeviceRepository.findPushTargets(TokenType.EXPO, sourceId, news.getCategory(), news.getTitle());
    }

    private List<ExpoPushMessage> buildMessages(List<News> newsList) {
        LocalDateTime cutoff = publishCutoff(Instant.now(), properties.getNewsMaxAgeDays());
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

    /**
     * 이 시각보다 앞선 작성일의 소식은 푸시하지 않는다 — 한국 날짜 기준 오늘에서 maxAgeDays 일 전 0시.
     * 예전엔 서버 시간대(UTC) 날짜로 셌기 때문에 KST 00~09시에는 하루가 밀려, 하루 더 지난 글까지 푸시됐다.
     */
    static LocalDateTime publishCutoff(Instant now, long maxAgeDays) {
        return LocalDate.ofInstant(now, KST).minusDays(maxAgeDays).atStartOfDay();
    }
}
