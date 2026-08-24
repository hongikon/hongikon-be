package com.hongmap.hongmapbackend.crawler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적 크롤링 실행 진입점.
 *
 * cron은 크롤링 부하와 공지 최신성 사이의 임시 타협점이다 — crawler.schedule.cron 프로퍼티로
 * 언제든 재정의할 수 있게 해뒀다(기본값: 매 정시).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final CrawlerService crawlerService;

    @Scheduled(cron = "${crawler.schedule.cron:0 0 * * * *}")
    public void runScheduledCrawl() {
        log.info("정기 크롤링 시작");
        int savedCount = crawlerService.crawlAll();
        log.info("정기 크롤링 종료 — 신규 {}건", savedCount);
    }
}
