package com.hongmap.hongmapbackend.crawler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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

    public record Snapshot(boolean running, LocalDateTime lastStartedAt, LocalDateTime lastFinishedAt,
                           Integer lastSavedCount, String lastError, Trigger lastTrigger) {
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

    public int run(Trigger trigger) {
        if (!running.compareAndSet(false, true)) {
            throw new AlreadyRunningException();
        }
        lastStartedAt = LocalDateTime.now();
        lastTrigger = trigger;
        try {
            int saved = crawlerService.crawlAll();
            lastSavedCount = saved;
            lastError = null;
            return saved;
        } catch (RuntimeException e) {
            lastSavedCount = null;
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            lastFinishedAt = LocalDateTime.now();
            running.set(false);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(running.get(), lastStartedAt, lastFinishedAt, lastSavedCount, lastError, lastTrigger);
    }
}
