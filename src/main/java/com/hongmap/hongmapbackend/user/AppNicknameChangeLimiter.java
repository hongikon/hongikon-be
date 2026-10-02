package com.hongmap.hongmapbackend.user;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 닉네임을 자주 바꿔 가며 다른 사람인 척하는 걸 막는 가벼운 제한: 사용자마다 24시간에 {@link #MAX_CHANGES}번.
 * 서버 한 대 메모리에만 두므로 재시작하면 초기화된다(엄격한 제한이 목적이 아니다).
 */
@Component
public class AppNicknameChangeLimiter {

    static final int MAX_CHANGES = 5;
    static final Duration WINDOW = Duration.ofHours(24);

    private final Map<Long, Deque<Instant>> history = new ConcurrentHashMap<>();
    private final Clock clock;

    public AppNicknameChangeLimiter() {
        this(Clock.systemUTC());
    }

    AppNicknameChangeLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 바꿀 수 있으면 기록하고 true. 한도를 넘었으면 false. */
    public boolean tryAcquire(Long userId) {
        Instant now = clock.instant();
        Deque<Instant> times = history.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(now.minus(WINDOW))) {
                times.pollFirst();
            }
            if (times.size() >= MAX_CHANGES) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
