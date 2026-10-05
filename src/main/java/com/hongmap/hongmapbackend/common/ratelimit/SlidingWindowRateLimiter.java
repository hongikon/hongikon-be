package com.hongmap.hongmapbackend.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키(사용자 id·IP 등)마다 "최근 window 안에 max 번"을 세는 가벼운 메모리 제한.
 * ReportImageService.acquireQuota·AppNicknameChangeLimiter 와 같은 방식(시각 큐를 키마다 두고 오래된 것부터 버림)을 여러 곳에서
 * 쓰려고 뺐다. 서버 한 대 메모리에만 두므로 재시작하면 초기화되고, 서버를 여러 대로 늘리면 대수만큼 느슨해진다 —
 * 엄격한 제한이 아니라 도배·스크립트 남용을 막는 용도다.
 *
 * 키가 {@link #CLEANUP_THRESHOLD} 개를 넘으면 창 밖으로 지난 키를 한 번에 치운다(메모리가 무한정 늘지 않게).
 */
public class SlidingWindowRateLimiter {

    static final int CLEANUP_THRESHOLD = 10_000;

    private final int max;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> history = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int max, Duration window, Clock clock) {
        if (max < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("max 는 1 이상, window 는 0 보다 커야 합니다.");
        }
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    /** 이번 요청을 받을 수 있으면 기록하고 true, 한도를 넘었으면 기록하지 않고 false. */
    public boolean tryAcquire(String key) {
        return tryAcquireAll(key);
    }

    /**
     * 여러 키를 한꺼번에 확인한다(예: IP 와 사용자 둘 다). 하나라도 꽉 찼으면 아무것도 기록하지 않고 false —
     * 한쪽만 기록돼 거절된 요청이 다른 쪽 한도를 깎지 않게 한다.
     */
    public boolean tryAcquireAll(String... keys) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);
        // 키 순서를 고정해 두 요청이 서로 반대 순서로 잠그는 일(교착)을 막는다.
        String[] sorted = Arrays.stream(keys).distinct().sorted().toArray(String[]::new);
        boolean acquired = acquireNested(sorted, 0, now, windowStart);
        if (history.size() > CLEANUP_THRESHOLD) {
            history.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    Instant last = entry.getValue().peekLast();
                    return last == null || !last.isAfter(windowStart);
                }
            });
        }
        return acquired;
    }

    /** keys[index..] 의 시각 큐를 차례로 잠그고, 전부 자리가 있을 때만 모두에 now 를 기록한다. */
    private boolean acquireNested(String[] keys, int index, Instant now, Instant windowStart) {
        if (index == keys.length) {
            return true;
        }
        Deque<Instant> times = history.computeIfAbsent(keys[index], k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(windowStart)) {
                times.pollFirst();
            }
            if (times.size() >= max || !acquireNested(keys, index + 1, now, windowStart)) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
