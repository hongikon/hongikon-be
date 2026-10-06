package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.crawler.CrawlerRunTracker;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자 대시보드 한 화면 분량의 요약 */
public record AdminOverviewResponse(
        Server server,
        Reports reports,
        Feedback feedback,
        News news,
        Crawler crawler
) {
    public record Server(String version, String buildTime) {
    }

    /** upcoming: 승인했지만 아직 시작 전(예정) — 시작 시각이 되면 '노출 중'으로 넘어간다. */
    public record Reports(long pending, long active, long hidden, long rejected, long upcoming) {
    }

    public record Feedback(long open) {
    }

    public record News(long total, long missingDepartment) {
    }

    /** lastRequestCount 이하 4개는 크롤링 최적화(2026-10)로 추가 — 학교 서버 요청 수·소요 시간·실패/건너뛴 게시판(출처명). */
    public record Crawler(boolean running, LocalDateTime lastStartedAt, LocalDateTime lastFinishedAt,
                          Integer lastSavedCount, String lastError, String lastTrigger,
                          Long lastRequestCount, Long lastDurationMs,
                          List<String> lastFailedBoards, List<String> lastSkippedBoards) {
        public static Crawler of(CrawlerRunTracker.Snapshot s) {
            return new Crawler(s.running(), s.lastStartedAt(), s.lastFinishedAt(), s.lastSavedCount(), s.lastError(),
                    s.lastTrigger() == null ? null : s.lastTrigger().name(),
                    s.lastRequestCount(), s.lastDurationMs(), s.lastFailedBoards(), s.lastSkippedBoards());
        }
    }
}
