package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 크롤러가 새로 저장한 소식을 구독자 기기로 푸시한다(Expo Push API).
 *
 * 한 번의 크롤링에서 모은 새 소식을 기기·게시판마다 하나의 메시지로 묶은 뒤(같은 게시판 새 글 N건 → "… 새 글 N건") {@value ExpoPushClient#MAX_BATCH_SIZE}개씩 묶어 보낸다(ExpoPushSender)
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
        // sourceId가 없으면 구독 기준은 :sourceId IS NOT NULL에서 걸러지므로 목록 값은 쓰이지 않는다(빈 IN 방지용 자리값).
        List<String> subscriberSourceIds = sourceId != null ? CrawlerBoards.subscriberSourceIds(sourceId) : List.of("");
        return userDeviceRepository.findPushTargets(
                TokenType.EXPO, sourceId, subscriberSourceIds, news.getCategory(), news.getTitle());
    }

    /**
     * 대상 판정은 소식마다 따로 하고(findTargets), 메시지는 기기 토큰 + 소식의 게시판(sourceId)마다 하나로 묶는다.
     * 한 크롤링에서 같은 게시판에 새 글이 여러 건 올라오면 기기마다 알림이 하나만 간다(운영: 새 글 13건 → 메시지 120건이었다).
     * 키워드 알림으로 받은 글도 묶는 기준은 그 글의 게시판이다. sourceId 가 없는 글은 묶지 않는다.
     */
    private List<ExpoPushMessage> buildMessages(List<News> newsList) {
        LocalDateTime cutoff = publishCutoff(Instant.now(), properties.getNewsMaxAgeDays());
        // (토큰, 게시판) → 그 기기가 받을 그 게시판 소식들. 처음 나온 순서를 지킨다.
        Map<GroupKey, List<News>> groups = new LinkedHashMap<>();

        for (int i = 0; i < newsList.size(); i++) {
            News news = newsList.get(i);
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
                String board = groupBoard(news, i);
                for (String token : tokens) {
                    List<News> group = groups.computeIfAbsent(new GroupKey(token, board), k -> new ArrayList<>());
                    if (!group.contains(news)) {
                        group.add(news);
                    }
                }
            } catch (Exception e) {
                log.warn("푸시 대상 조회 실패 (newsId={}): {}", news.getId(), e.getMessage());
            }
        }

        List<ExpoPushMessage> messages = new ArrayList<>(groups.size());
        groups.forEach((key, group) -> messages.add(toMessage(group, key.token())));
        return messages;
    }

    private record GroupKey(String token, String board) {
    }

    /** 묶는 기준. sourceId 가 없는 글은 소식마다 따로 보낸다(어느 게시판인지 모르므로). */
    private static String groupBoard(News news, int index) {
        if (news.getSourceId() != null && !news.getSourceId().isBlank()) {
            return "source:" + news.getSourceId();
        }
        return "news#" + index;
    }

    /** 가장 최신 글 — 작성일(published_at) 기준, 같으면 id 가 큰(나중에 저장된) 글. */
    private static final Comparator<News> NEWEST_FIRST = Comparator
            .comparing(News::getPublishedAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
            .thenComparing(News::getId, Comparator.nullsLast(Comparator.<Long>reverseOrder()));

    private ExpoPushMessage toMessage(List<News> group, String token) {
        if (group.size() == 1) {
            return toMessage(group.get(0), token);
        }
        News newest = group.stream().min(NEWEST_FIRST).orElseThrow();
        int count = group.size();
        return ExpoPushMessage.of(
                token,
                sourceLabel(newest) + " 새 글 " + count + "건",
                newest.getTitle() + " 외 " + (count - 1) + "건",
                Map.of("type", DATA_TYPE_NEWS, "newsId", newest.getId())
        );
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
