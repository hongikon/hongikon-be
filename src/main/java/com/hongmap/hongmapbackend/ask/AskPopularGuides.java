package com.hongmap.hongmapbackend.ask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * '요즘 많이 찾는 안내'(11-07) — 앱 물어보기 탭이 인사(하이·ㅎㅇ)에 답할 때 칩으로 보이는 안내 id.
 * 최근 {@link #WINDOW_DAYS}일 기록(ask_conversation_logs)에서 답·되묻기에 쓴 안내 id 를 서로 다른 대화 수로 센다.
 * {@link #MIN_CONVERSATIONS}개 대화 이상에서 쓴 안내만, 많은 순으로 {@link #LIMIT}개.
 *
 * 개인정보: 안내 id(카탈로그에 있는 것)만 내보낸다. 질문 원문·건수·대화 id 는 내보내지 않는다.
 * 적은 대화에서만 나온 안내는 빼서, 누가 무엇을 물었는지 짐작할 수 없게 한다.
 * 한 시간마다 다시 센다(기록은 하루 몇백 건이라 메모리에서 센다).
 */
@Slf4j
@Component
public class AskPopularGuides {

    static final int WINDOW_DAYS = 7;
    static final int MIN_CONVERSATIONS = 5;
    static final int LIMIT = 6;
    static final Duration REFRESH = Duration.ofHours(1);

    private final AskConversationLogRepository repository;
    private final AskCatalog catalog;
    private final Clock clock;
    private volatile List<String> cached = List.of();
    private volatile Instant computedAt;

    @Autowired
    public AskPopularGuides(AskConversationLogRepository repository, AskCatalog catalog) {
        this(repository, catalog, Clock.systemUTC());
    }

    AskPopularGuides(AskConversationLogRepository repository, AskCatalog catalog, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.clock = clock;
    }

    public List<String> ids() {
        Instant now = clock.instant();
        Instant at = computedAt;
        if (at == null || !now.isBefore(at.plus(REFRESH))) {
            synchronized (this) {
                if (computedAt == null || !now.isBefore(computedAt.plus(REFRESH))) {
                    cached = compute(now);
                    computedAt = now;
                }
            }
        }
        return cached;
    }

    private List<String> compute(Instant now) {
        try {
            LocalDateTime since = LocalDateTime.ofInstant(now.minus(Duration.ofDays(WINDOW_DAYS)), ZoneOffset.UTC);
            return rank(repository.findGuideUses(since, List.of(AskConversationLog.Outcome.ANSWERED, AskConversationLog.Outcome.QUESTION)));
        } catch (RuntimeException e) {
            // 세지 못하면 칩 없이 인사만 한다(앱은 빈 목록이면 기본 안내를 보인다). 지난 값은 그대로 둔다.
            log.warn("요즘 많이 찾는 안내를 세지 못함: {}", e.toString());
            return cached;
        }
    }

    /** (대화 id, 쉼표로 이은 안내 id) 행들 → 서로 다른 대화 수가 많은 안내 순. 대화 id 가 없는 행은 세지 않는다. */
    List<String> rank(List<Object[]> rows) {
        Map<String, Set<String>> conversations = new HashMap<>();
        for (Object[] row : rows) {
            String conversation = (String) row[0];
            String nodeIds = (String) row[1];
            if (conversation == null || conversation.isBlank() || nodeIds == null) {
                continue;
            }
            for (String id : nodeIds.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty() && !AskCatalog.NONE.equals(trimmed) && catalog.contains(trimmed)) {
                    conversations.computeIfAbsent(trimmed, key -> new HashSet<>()).add(conversation);
                }
            }
        }
        return conversations.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= MIN_CONVERSATIONS)
                .sorted(Comparator.<Map.Entry<String, Set<String>>>comparingInt(entry -> entry.getValue().size()).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(LIMIT)
                .map(Map.Entry::getKey)
                .toList();
    }
}
