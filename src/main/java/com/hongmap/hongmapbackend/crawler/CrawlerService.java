package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
import com.hongmap.hongmapbackend.crawler.config.CrawlerProperties;
import com.hongmap.hongmapbackend.crawler.parser.ArticleDetail;
import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
import com.hongmap.hongmapbackend.crawler.parser.BoardParser;
import com.hongmap.hongmapbackend.crawler.parser.BoardParserRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 게시판 전체를 순회하며 크롤링을 오케스트레이션한다.
 * hongmap(프론트) scripts/crawler/crawl.mjs의 crawlBoard()에 대응하되, 여러 게시판을 한 번에 도는
 * 진입점(crawlAll)까지 포함한다. 실제 크롤링(요청+파싱)은 CrawlerHttpClient/BoardParser,
 * 저장은 NewsCrawlStorageService에 위임하고 여기서는 흐름 제어만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final CrawlerHttpClient httpClient;
    private final BoardParserRegistry parserRegistry;
    private final NewsCrawlStorageService storageService;
    private final CrawlerProperties properties;

    /** 게시판 하나가 실패해도 나머지 게시판 수집은 계속한다. */
    public void crawlAll() {
        for (BoardConfig board : CrawlerBoards.ALL) {
            try {
                crawlBoard(board);
            } catch (Exception e) {
                log.warn("게시판 크롤링 실패: {} ({})", board.source(), board.listUrl(), e);
            }
        }
    }

    private void crawlBoard(BoardConfig board) {
        BoardParser parser = parserRegistry.resolve(board.parser());
        int saved = 0;

        for (int page = 0; page < properties.getDefaultPages(); page++) {
            if (page > 0) {
                httpClient.politeDelay();
            }

            String listUrl = parser.buildListUrl(board.listUrl(), page, properties.getPageSize());
            Document listDocument = httpClient.get(listUrl);
            List<ArticleSummary> summaries = parser.parseList(listDocument, board.listUrl(), board.tableSummary());

            if (summaries.isEmpty()) {
                break;
            }

            for (ArticleSummary summary : summaries) {
                if (saved >= board.maxItems()) {
                    break;
                }
                if (isExcluded(board, summary)) {
                    continue;
                }
                // 이미 저장된 글이면 상세 요청까지 갈 필요가 없다(불필요한 트래픽 방지).
                if (storageService.alreadyExists(summary.link())) {
                    continue;
                }

                ArticleDetail detail = fetchDetail(parser, summary);
                if (storageService.save(board, summary, detail)) {
                    saved++;
                }
            }
        }

        log.info("게시판 크롤링 완료: {} — 신규 {}건", board.source(), saved);
    }

    private boolean isExcluded(BoardConfig board, ArticleSummary summary) {
        return board.excludeTitlePattern() != null
                && board.excludeTitlePattern().matcher(summary.title()).find();
    }

    /** 상세 수집 실패는 전체 크롤링을 막지 않는다 — 실패하면 본문 없이(목록 정보만으로) 저장한다. */
    private ArticleDetail fetchDetail(BoardParser parser, ArticleSummary summary) {
        if (!parser.supportsDetail()) {
            return null;
        }

        try {
            httpClient.politeDelay();
            Document viewDocument = httpClient.get(summary.link());
            return parser.parseView(viewDocument, summary.link());
        } catch (Exception e) {
            log.warn("상세 수집 실패 (articleNo={}): {}", summary.articleNo(), e.getMessage());
            return null;
        }
    }
}
