package com.hongmap.hongmapbackend.crawler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 크롤링을 한 번에 하나만 돌리고, 마지막 실행 결과를 관리자 대시보드에 보여주기 위해 기억한다.
 * 서버 메모리에만 있으므로 재시작하면 첫 실행 전까지 비어 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerRunTracker {

    public enum Trigger { SCHEDULED, MANUAL }

    /**
     * lastRequestCount~lastSkippedBoards는 마지막으로 끝까지 돈 실행의 요약(CrawlResult). 실행이 예외로 끝나면 null.
     * 크롤링 최적화(증분 수집·건너뛰기) 효과를 대시보드에서 바로 보려고 넣었다.
     */
    public record Snapshot(boolean running, LocalDateTime lastStartedAt, LocalDateTime lastFinishedAt,
                           Integer lastSavedCount, String lastError, Trigger lastTrigger,
                           Long lastRequestCount, Long lastDurationMs,
                           List<String> lastFailedBoards, List<String> lastSkippedBoards) {
    }

    /** 다른 실행이 이미 돌고 있을 때 */
    public static class AlreadyRunningException extends RuntimeException {
        public AlreadyRunningException() {
            super("이미 크롤링이 진행 중입니다.");
        }
    }

    private final CrawlerService crawlerService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile LocalDateTime lastStartedAt;
    private volatile LocalDateTime lastFinishedAt;
    private volatile Integer lastSavedCount;
    private volatile String lastError;
    private volatile Trigger lastTrigger;
    private volatile CrawlResult lastResult;

    public int run(Trigger trigger) {
        if (!running.compareAndSet(false, true)) {
            throw new AlreadyRunningException();
        }
        lastStartedAt = LocalDateTime.now();
        lastTrigger = trigger;
        try {
            CrawlResult result = crawlerService.crawlAll();
            lastResult = result;
            lastSavedCount = result.savedCount();
            lastError = null;
            return result.savedCount();
        } catch (RuntimeException e) {
            lastResult = null;
            lastSavedCount = null;
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            lastFinishedAt = LocalDateTime.now();
            running.set(false);
        }
    }

    public Snapshot snapshot() {
        CrawlResult result = lastResult;
        return new Snapshot(running.get(), lastStartedAt, lastFinishedAt, lastSavedCount, lastError, lastTrigger,
                result != null ? result.requestCount() : null,
                result != null ? result.durationMs() : null,
                result != null ? result.failedBoards() : null,
                result != null ? result.skippedBoards() : null);
    }
}
