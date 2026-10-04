package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 계속 죽어 있는 게시판(사이트 개편·서버 장애)을 매시간 재시도+타임아웃으로 두드리지 않게 하는 게시판 단위 차단기.
 *
 * 게시판 하나가 실행 단위로 {@code crawler.failure-threshold}번 연속 실패하면 {@code crawler.failure-cooldown-minutes}분 동안
 * 건너뛴다. 시간이 지나면 한 번 다시 시도해 성공하면 정상화(카운트 초기화), 또 실패하면 다시 그만큼 쉰다.
 * 죽은 게시판 하나가 실행마다 요청 4회(재시도 3회) + 최대 60초(타임아웃 15초×4)를 쓰던 것을 막는다.
 *
 * 상태는 서버 메모리에만 있다 — 재시작하면 모두 정상으로 돌아가 한 번씩 다시 시도한다(안전한 쪽).
 * 게시판 식별은 목록 URL(대학공지 6종은 boardKey가 같아 쓸 수 없다).
 */
@Slf4j
@Component
public class CrawlerBoardCircuitBreaker {

    private record State(int consecutiveFailures, Instant openUntil) {
    }

    private final CrawlerProperties properties;
    private final Clock clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    @Autowired
    public CrawlerBoardCircuitBreaker(CrawlerProperties properties) {
        this(properties, Clock.systemUTC());
    }

    CrawlerBoardCircuitBreaker(CrawlerProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** 이번 실행에서 이 게시판을 요청해도 되는지. 쉬는 중이면 false. */
    public boolean allowRequest(BoardConfig board) {
        State state = states.get(board.listUrl());
        return state == null || state.openUntil() == null || !clock.instant().isBefore(state.openUntil());
    }

    public void recordSuccess(BoardConfig board) {
        State previous = states.remove(board.listUrl());
        if (previous != null && previous.openUntil() != null) {
            log.info("게시판 크롤링 복구: {} (연속 실패 {}회 뒤 성공)", board.source(), previous.consecutiveFailures());
        }
    }

    public void recordFailure(BoardConfig board) {
        int threshold = properties.getFailureThreshold();
        if (threshold <= 0) {
            return;
        }
        State updated = states.compute(board.listUrl(), (key, previous) -> {
            int failures = (previous == null ? 0 : previous.consecutiveFailures()) + 1;
            Instant openUntil = failures >= threshold
                    ? clock.instant().plus(Duration.ofMinutes(properties.getFailureCooldownMinutes()))
                    : null;
            return new State(failures, openUntil);
        });
        if (updated.openUntil() != null) {
            log.warn("게시판 {}회 연속 실패 — {}분 동안 건너뜀: {} ({})", updated.consecutiveFailures(),
                    properties.getFailureCooldownMinutes(), board.source(), board.listUrl());
        }
    }
}
