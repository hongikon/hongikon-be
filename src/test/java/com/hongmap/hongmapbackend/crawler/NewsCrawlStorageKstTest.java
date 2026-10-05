package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
import com.hongmap.hongmapbackend.news.News;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작성일을 못 읽은 글의 published_at 대체값은 한국 시각 "지금"이어야 한다. 예전엔 서버 시간대(UTC)의 지금이라
 * 9시간 이르게 찍혀, KST 00~09시에 올라온 글이 전날 글로 보였다.
 * (NewsCrawlStorageServiceTest 와 따로 둔다 — 크롤러 PR 과 같은 파일을 고치지 않게.)
 */
@SpringBootTest
@ActiveProfiles("test")
class NewsCrawlStorageKstTest {

    @Autowired NewsCrawlStorageService storageService;

    @Test
    void 작성일을_못_읽으면_한국_시각_지금으로_저장한다() {
        BoardConfig board = CrawlerBoards.ALL.stream()
                .filter(b -> b.boardKey().equals("arch-ev")).findFirst().orElseThrow();
        String token = UUID.randomUUID().toString();
        ArticleSummary summary = new ArticleSummary(token, "날짜 없는 글 " + token, "", "", null, false,
                "https://arch.hongik.ac.kr/kor/news/event.php?m=v&idx=" + token);

        News saved = storageService.save(board, summary, null).orElseThrow();

        LocalDateTime nowKst = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        assertThat(Duration.between(saved.getPublishedAt(), nowKst).abs()).isLessThan(Duration.ofMinutes(5));
    }
}
