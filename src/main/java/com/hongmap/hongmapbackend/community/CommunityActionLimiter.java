package com.hongmap.hongmapbackend.community;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🔥·관심·👍 누르기 빈도 제한: 사용자마다 1분에 report.reaction.rate-per-minute(기본 20)번.
 * 끄면 행이 지워져 DB 로는 셀 수 없어 서버 메모리에 둔다(서버 한 대 기준, 재시작하면 초기화 — 엄격한 제한이 목적이 아니다).
 */
@Component
public class CommunityActionLimiter {

    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_USERS = 50_000;

    private final Map<Long, Deque<Instant>> history = new ConcurrentHashMap<>();
    private final int limit;
    private Clock clock = Clock.systemUTC();

    public CommunityActionLimiter(@Value("${report.reaction.rate-per-minute:20}") int limit) {
        this.limit = limit;
    }

    /** 할 수 있으면 기록하고 true. 한도를 넘었으면 false. */
    public boolean tryAcquire(Long userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        if (history.size() > MAX_TRACKED_USERS) {
            history.values().removeIf(times -> {
                synchronized (times) {
                    return times.isEmpty() || !times.peekLast().isAfter(cutoff);
                }
            });
        }
        Deque<Instant> times = history.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    void resetForTest() {
        history.clear();
    }
}
