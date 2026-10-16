package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 키 선택(로그인이면 사용자, 아니면 IP)과 한도. */
class AskFeedbackRateLimiterTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);

    private static void assertTooMany(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void 게스트는_IP_마다_센다() {
        AskFeedbackRateLimiter limiter = new AskFeedbackRateLimiter(2, Duration.ofMinutes(10), clock);
        limiter.acquire("1.1.1.1", null);
        limiter.acquire("1.1.1.1", null);
        assertTooMany(() -> limiter.acquire("1.1.1.1", null));
        assertThatCode(() -> limiter.acquire("2.2.2.2", null)).doesNotThrowAnyException();
    }

    @Test
    void 로그인_사용자는_IP가_달라도_사용자_기준이고_같은_IP의_게스트와_한도를_나누지_않는다() {
        AskFeedbackRateLimiter limiter = new AskFeedbackRateLimiter(2, Duration.ofMinutes(10), clock);
        limiter.acquire("1.1.1.1", 7L);
        limiter.acquire("2.2.2.2", 7L);
        assertTooMany(() -> limiter.acquire("3.3.3.3", 7L));
        assertThatCode(() -> limiter.acquire("1.1.1.1", null)).doesNotThrowAnyException();
    }
}
