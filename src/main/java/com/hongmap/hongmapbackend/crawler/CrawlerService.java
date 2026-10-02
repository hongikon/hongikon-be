package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
import com.hongmap.hongmapbackend.crawler.config.CrawlerProperties;
import com.hongmap.hongmapbackend.crawler.parser.ArticleDetail;
import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
import com.hongmap.hongmapbackend.crawler.parser.BoardParser;
import com.hongmap.hongmapbackend.crawler.parser.BoardParserRegistry;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.push.NewsPushDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private final NewsPushDispatcher pushDispatcher;
    private final CrawlerProperties properties;

    /**
     * 게시판 하나가 실패해도 나머지 게시판 수집은 계속한다. 반환값은 전체 게시판에서 신규 저장된 건수 합계.
     * 새 소식 푸시는 전체 게시판 수집이 끝난 뒤 한 번에 보낸다(배치 발송, 수집 속도에 영향 없음). 푸시 실패는 결과에 영향 없다.
     */
    public int crawlAll() {
        List<News> newNews = new ArrayList<>();
        List<String> emptyBoards = new ArrayList<>();
        for (BoardConfig board : CrawlerBoards.ALL) {
            try {
                if (crawlBoard(board, newNews) == 0) {
                    emptyBoards.add(board.boardKey() + "(" + board.sourceId() + ")");
                }
            } catch (Exception e) {
                log.warn("게시판 크롤링 실패: {} ({})", board.source(), board.listUrl(), e);
            }
        }
        // 첫 페이지에서 글을 한 건도 못 읽은 게시판. 게시판이 비어 있거나(예: 조소과) URL·마크업이 바뀐 경우라 운영자가 확인할 목록이다.
        if (!emptyBoards.isEmpty()) {
            log.warn("목록 0건 게시판 {}개: {}", emptyBoards.size(), String.join(", ", emptyBoards));
        }

        try {
            pushDispatcher.dispatch(newNews);
        } catch (Exception e) {
            log.warn("새 소식 푸시 발송 실패", e);
        }
        return newNews.size();
    }

    /** 새로 저장한 소식을 newNews에 더하고, 첫 페이지 목록에서 읽은 글 수를 돌려준다(0이면 비었거나 파싱 실패). */
    private int crawlBoard(BoardConfig board, List<News> newNews) {
        BoardParser parser = parserRegistry.resolve(board.parser());
        int saved = 0;
        int firstPageCount = 0;

        for (int page = 0; page < properties.getDefaultPages(); page++) {
            if (page > 0) {
                httpClient.politeDelay();
            }

            String listUrl = parser.buildListUrl(board.listUrl(), page, properties.getPageSize());
            Document listDocument = httpClient.get(listUrl);
            List<ArticleSummary> summaries = parser.parseList(listDocument, board.listUrl(), board.tableSummary());
            if (page == 0) {
                firstPageCount = summaries.size();
            }

            if (summaries.isEmpty()) {
                break;
            }

            List<String> existingUrls = new ArrayList<>();
            for (ArticleSummary summary : summaries) {
                if (saved >= board.maxItems()) {
                    break;
                }
                if (isExcluded(board, summary)) {
                    continue;
                }
                // 이미 저장된 글이면 상세 요청까지 갈 필요가 없다(불필요한 트래픽 방지).
                if (storageService.alreadyExists(board, summary, parser.hasStableArticleUrl())) {
                    // 링크가 매번 바뀌는 게시판은 이번 링크가 DB에 없으니 source_id 채우기 대상에서 뺀다.
                    if (parser.hasStableArticleUrl()) {
                        existingUrls.add(summary.link());
                    }
                    continue;
                }

                ArticleDetail detail = fetchDetail(parser, summary);
                Optional<News> savedNews = storageService.save(board, summary, detail);
                if (savedNews.isPresent()) {
                    newNews.add(savedNews.get());
                    saved++;
                }
            }
            // source_id 컬럼 도입 전에 저장된 글이면 이번에 다시 만난 김에 게시판 출처를 채운다(페이지당 UPDATE 1회).
            storageService.fillMissingSourceId(board, existingUrls);
        }

        log.info("게시판 크롤링 완료: {} — 목록 {}건, 신규 {}건", board.source(), firstPageCount, saved);
        return firstPageCount;
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
