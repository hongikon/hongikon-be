package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.crawler.CrawlerRunTracker;

import java.time.LocalDateTime;

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

    public record Reports(long pending, long active, long hidden, long rejected) {
    }

    public record Feedback(long open) {
    }

    public record News(long total, long missingDepartment) {
    }

    public record Crawler(boolean running, LocalDateTime lastStartedAt, LocalDateTime lastFinishedAt,
                          Integer lastSavedCount, String lastError, String lastTrigger) {
        public static Crawler of(CrawlerRunTracker.Snapshot s) {
            return new Crawler(s.running(), s.lastStartedAt(), s.lastFinishedAt(), s.lastSavedCount(), s.lastError(),
                    s.lastTrigger() == null ? null : s.lastTrigger().name());
        }
    }
}
