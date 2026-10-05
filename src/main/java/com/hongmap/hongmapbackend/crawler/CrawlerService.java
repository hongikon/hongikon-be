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

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 게시판 전체를 순회하며 크롤링을 오케스트레이션한다.
 * hongmap(프론트) scripts/crawler/crawl.mjs의 crawlBoard()에 대응하되, 여러 게시판을 한 번에 도는
 * 진입점(crawlAll)까지 포함한다. 실제 크롤링(요청+파싱)은 CrawlerHttpClient/BoardParser,
 * 저장은 NewsCrawlStorageService에 위임하고 여기서는 흐름 제어만 한다.
 *
 * 비용 줄이기(2026-10):
 * - 증분 수집: 페이지의 가장 오래된 글이 이미 저장돼 있으면 다음 페이지를 요청하지 않는다(평시 목록 요청 절반).
 * - 이미 저장된 글 판단은 페이지당 쿼리 1회(NewsCrawlStorageService#findKnown). 저장된 글은 상세도 받지 않는다(기존 동작).
 * - 게시판을 접속 서버(IP)별로 묶어 서버끼리만 병렬, 같은 서버 안에서는 순차 + 요청 사이 딜레이.
 * - 연속 실패한 게시판은 잠시 건너뛴다(CrawlerBoardCircuitBreaker).
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
    private final CrawlerBoardCircuitBreaker circuitBreaker;

    /** 목록 URL 호스트 → 서버 구분 키(IP). 테스트에서 가짜 호스트를 묶기 위해 바꿔 끼울 수 있게 열어둔다. */
    Function<String, String> serverResolver = CrawlerService::resolveServer;

    /**
     * 지난번에 실패한 게시판(목록 URL). 다음 성공 때까지 증분 중단을 끄고 default-pages를 다 훑는다 —
     * 1페이지 저장 후 2페이지 요청에서 실패했다면, 다음 실행은 1페이지가 전부 "이미 저장"이라 2페이지를 영영 안 받게 되기 때문.
     */
    private final Set<String> fullScanBoards = ConcurrentHashMap.newKeySet();

    /** 게시판 하나의 결과. 실패해도 그 전까지 저장한 글(news)은 푸시 대상에 남긴다(기존 동작). */
    private record BoardOutcome(List<News> news, int firstPageCount, boolean failed, boolean skipped) {
    }

    /**
     * 게시판 하나가 실패해도 나머지 게시판 수집은 계속한다.
     * 새 소식 푸시는 전체 게시판 수집이 끝난 뒤 한 번에 보낸다(배치 발송, 수집 속도에 영향 없음). 푸시 실패는 결과에 영향 없다.
     */
    public CrawlResult crawlAll() {
        return crawlAll(CrawlerBoards.ALL);
    }

    CrawlResult crawlAll(List<BoardConfig> boards) {
        long startedAt = System.currentTimeMillis();
        long requestsBefore = httpClient.requestCount();

        Map<BoardConfig, BoardOutcome> outcomes = new ConcurrentHashMap<>();
        runGroups(groupByServer(boards), outcomes);

        // 게시판 설정 순서대로 모은다 — 병렬로 돌아도 푸시 순서·로그가 실행마다 같다.
        List<News> newNews = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> empty = new ArrayList<>();
        for (BoardConfig board : boards) {
            BoardOutcome outcome = outcomes.get(board);
            if (outcome == null) {
                failed.add(board.source());
                continue;
            }
            newNews.addAll(outcome.news());
            if (outcome.skipped()) {
                skipped.add(board.source());
            } else if (outcome.failed()) {
                failed.add(board.source());
            } else if (outcome.firstPageCount() == 0) {
                empty.add(board.boardKey() + "(" + board.sourceId() + ")");
            }
        }
        // 첫 페이지에서 글을 한 건도 못 읽은 게시판. 게시판이 비어 있거나(예: 조소과) URL·마크업이 바뀐 경우라 운영자가 확인할 목록이다.
        if (!empty.isEmpty()) {
            log.warn("목록 0건 게시판 {}개: {}", empty.size(), String.join(", ", empty));
        }

        try {
            pushDispatcher.dispatch(newNews);
        } catch (Exception e) {
            log.warn("새 소식 푸시 발송 실패", e);
        }

        CrawlResult result = new CrawlResult(newNews.size(), boards.size(), failed, skipped, empty,
                httpClient.requestCount() - requestsBefore, System.currentTimeMillis() - startedAt);
        log.info("크롤링 요약: 게시판 {}개(실패 {}, 건너뜀 {}), 요청 {}회, 신규 {}건, {}ms{}{}",
                result.boardCount(), failed.size(), skipped.size(), result.requestCount(), result.savedCount(),
                result.durationMs(),
                failed.isEmpty() ? "" : " — 실패: " + String.join(", ", failed),
                skipped.isEmpty() ? "" : " — 건너뜀: " + String.join(", ", skipped));
        return result;
    }

    /**
     * 서버 묶음끼리만 병렬로 돈다(최대 crawler.parallel-servers). 한 묶음 안은 한 스레드가 순서대로 처리하므로
     * 같은 서버로 동시에 두 요청이 나가는 일은 없다.
     */
    private void runGroups(Map<String, List<BoardConfig>> groups, Map<BoardConfig, BoardOutcome> outcomes) {
        int threads = Math.max(1, Math.min(properties.getParallelServers(), groups.size()));
        if (threads == 1) {
            groups.values().forEach(group -> crawlGroup(group, outcomes));
            return;
        }

        AtomicInteger threadNo = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "crawler-" + threadNo.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (List<BoardConfig> group : groups.values()) {
                futures.add(executor.submit(() -> crawlGroup(group, outcomes)));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    // crawlGroup은 게시판별로 예외를 잡으므로 여기까지 오면 버그다. 결과가 없는 게시판은 실패로 집계된다.
                    log.warn("게시판 묶음 크롤링 중 예상치 못한 오류", e.getCause());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
    }

    private void crawlGroup(List<BoardConfig> group, Map<BoardConfig, BoardOutcome> outcomes) {
        boolean first = true;
        for (BoardConfig board : group) {
            if (!circuitBreaker.allowRequest(board)) {
                outcomes.put(board, new BoardOutcome(List.of(), 0, false, true));
                continue;
            }
            // 같은 서버의 게시판 사이에도 딜레이를 둔다(예전엔 앞 게시판 마지막 요청 → 다음 게시판 첫 요청이 바로 붙었다).
            if (!first) {
                httpClient.politeDelay();
            }
            first = false;

            List<News> boardNews = new ArrayList<>();
            try {
                int firstPageCount = crawlBoard(board, boardNews, !fullScanBoards.contains(board.listUrl()));
                fullScanBoards.remove(board.listUrl());
                circuitBreaker.recordSuccess(board);
                outcomes.put(board, new BoardOutcome(boardNews, firstPageCount, false, false));
            } catch (Exception e) {
                circuitBreaker.recordFailure(board);
                fullScanBoards.add(board.listUrl());
                log.warn("게시판 크롤링 실패: {} ({}) — {}", board.source(), board.listUrl(), e.toString());
                log.debug("게시판 크롤링 실패 상세", e);
                outcomes.put(board, new BoardOutcome(boardNews, 0, true, false));
            }
        }
    }

    /** 접속 서버(IP)별로 게시판을 묶는다. 순서는 게시판 설정 순서를 따른다. */
    private Map<String, List<BoardConfig>> groupByServer(List<BoardConfig> boards) {
        Map<String, String> serverByHost = new LinkedHashMap<>();
        Map<String, List<BoardConfig>> groups = new LinkedHashMap<>();
        for (BoardConfig board : boards) {
            String host = hostOf(board.listUrl());
            String server = serverByHost.computeIfAbsent(host, serverResolver);
            groups.computeIfAbsent(server, key -> new ArrayList<>()).add(board);
        }
        return groups;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null ? host : url;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    /** DNS를 못 풀면 호스트명 자체를 키로 쓴다(그 게시판은 어차피 요청 단계에서 실패한다). */
    private static String resolveServer(String host) {
        try {
            return InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            return host;
        }
    }

    /**
     * 새로 저장한 소식을 newNews에 더하고, 첫 페이지 목록에서 읽은 글 수를 돌려준다(0이면 비었거나 파싱 실패).
     * incremental=false면 이미 본 구간에 닿아도 멈추지 않는다(지난번 실패 뒤 첫 실행, 또는 crawler.incremental=false).
     */
    int crawlBoard(BoardConfig board, List<News> newNews, boolean incremental) {
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

            List<ArticleSummary> candidates = summaries.stream()
                    .filter(summary -> !isExcluded(board, summary))
                    .toList();
            // 이미 저장된 글 판단을 페이지당 한 번에(글마다 exists 쿼리 X). 저장된 글은 상세 요청까지 갈 필요가 없다.
            NewsCrawlStorageService.KnownArticles known =
                    storageService.findKnown(board, candidates, parser.hasStableArticleUrl());

            for (ArticleSummary summary : candidates) {
                if (saved >= board.maxItems()) {
                    break;
                }
                if (known.isKnown(summary)) {
                    continue;
                }

                ArticleDetail detail = fetchDetail(parser, summary);
                Optional<News> savedNews = storageService.save(board, summary, detail);
                if (savedNews.isPresent()) {
                    newNews.add(savedNews.get());
                    saved++;
                }
            }
            // source_id 컬럼 도입 전에 저장된 글이면 이번에 다시 만난 김에 게시판 출처를 채운다(채울 행이 있을 때만 UPDATE).
            storageService.fillMissingSourceId(board, known.linksMissingSourceId());

            if (saved >= board.maxItems() || (incremental && reachedKnownRange(candidates, known))) {
                break;
            }
        }

        log.info("게시판 크롤링 완료: {} — 목록 {}건, 신규 {}건", board.source(), firstPageCount, saved);
        return firstPageCount;
    }

    /**
     * 목록은 최신순이라 페이지의 마지막(가장 오래된) 글이 이미 저장돼 있으면 그 뒤 페이지는 전부 이전 실행에서 본 구간이다.
     * "페이지에 아는 글이 하나라도 있으면 멈춤"으로 하면 안 된다 — 상단 고정 공지(오래된 글이 매 페이지 맨 위에 붙음)
     * 때문에 새 글이 한 페이지를 넘게 쌓여도 2페이지를 안 받게 된다. 그래서 맨 마지막 글만 본다.
     * 제외 패턴으로 다 걸러져 판단할 글이 없으면 예전처럼 다음 페이지로 간다.
     */
    private boolean reachedKnownRange(List<ArticleSummary> candidates, NewsCrawlStorageService.KnownArticles known) {
        return properties.isIncremental()
                && !candidates.isEmpty()
                && known.isKnown(candidates.get(candidates.size() - 1));
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
