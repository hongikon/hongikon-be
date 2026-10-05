package com.hongmap.hongmapbackend.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowRateLimiterTest {

    /** 테스트에서 시각을 직접 옮기는 Clock. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-05T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void 창_안에서_max_번까지만_받고_창이_지나면_다시_받는다() {
        MutableClock clock = new MutableClock();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, Duration.ofMinutes(10), clock);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue(); // 키마다 따로

        clock.now = clock.now.plus(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    @Test
    void 여러_키_중_하나라도_차면_아무것도_기록하지_않는다() {
        MutableClock clock = new MutableClock();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofMinutes(10), clock);

        assertThat(limiter.tryAcquire("user:1")).isTrue();
        // user:1 이 차 있어 거절 — ip:x 는 깎이지 않아야 한다.
        assertThat(limiter.tryAcquireAll("ip:x", "user:1")).isFalse();
        assertThat(limiter.tryAcquire("ip:x")).isTrue();
    }
}
