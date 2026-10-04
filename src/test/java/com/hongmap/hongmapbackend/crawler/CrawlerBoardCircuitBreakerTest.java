package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerProperties;
import com.hongmap.hongmapbackend.crawler.config.ParserType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 게시판 단위 연속 실패 차단기 — 연속일 때만 세고, 성공하면 초기화, 쿨다운 뒤 한 번 재시도. */
class CrawlerBoardCircuitBreakerTest {

    private final CrawlerServiceTest.MutableClock clock =
            new CrawlerServiceTest.MutableClock(Instant.parse("2026-10-05T00:00:00Z"));
    private final BoardConfig board =
            new BoardConfig("x", "x", "x", "https://x.test/list", null, ParserType.HONGIK, null, false, 100);

    private CrawlerBoardCircuitBreaker breaker(int threshold) {
        return new CrawlerBoardCircuitBreaker(
                new CrawlerProperties(50, 2, 0, 0, 1000, "test", true, 1, threshold, 60), clock);
    }

    @Test
    void 중간에_성공하면_연속_실패_횟수가_초기화된다() {
        CrawlerBoardCircuitBreaker breaker = breaker(3);
        breaker.recordFailure(board);
        breaker.recordFailure(board);
        breaker.recordSuccess(board);
        breaker.recordFailure(board);
        breaker.recordFailure(board);

        assertThat(breaker.allowRequest(board)).isTrue();
        breaker.recordFailure(board);
        assertThat(breaker.allowRequest(board)).isFalse();
    }

    @Test
    void 쿨다운_뒤_재시도에서_또_실패하면_다시_쿨다운만큼_쉰다() {
        CrawlerBoardCircuitBreaker breaker = breaker(2);
        breaker.recordFailure(board);
        breaker.recordFailure(board);
        assertThat(breaker.allowRequest(board)).isFalse();

        clock.advance(Duration.ofMinutes(60));
        assertThat(breaker.allowRequest(board)).isTrue();
        breaker.recordFailure(board);
        assertThat(breaker.allowRequest(board)).isFalse();

        clock.advance(Duration.ofMinutes(60));
        breaker.recordSuccess(board);
        assertThat(breaker.allowRequest(board)).isTrue();
    }

    @Test
    void threshold가_0이면_건너뛰지_않는다() {
        CrawlerBoardCircuitBreaker breaker = breaker(0);
        for (int i = 0; i < 10; i++) breaker.recordFailure(board);
        assertThat(breaker.allowRequest(board)).isTrue();
    }
}
