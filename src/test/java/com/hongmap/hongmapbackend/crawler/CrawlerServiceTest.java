package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerProperties;
import com.hongmap.hongmapbackend.crawler.config.ParserType;
import com.hongmap.hongmapbackend.crawler.parser.ArticleDetail;
import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
import com.hongmap.hongmapbackend.crawler.parser.BoardParser;
import com.hongmap.hongmapbackend.crawler.parser.BoardParserRegistry;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.push.NewsPushDispatcher;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 크롤링 흐름 제어(증분 중단·저장된 글 상세 생략·페이지 단위 중복 판단·연속 실패 건너뛰기·서버별 순차 병렬).
 * 네트워크·DB 없이 돈다 — HTTP는 요청 URL을 기록하는 가짜, 파서는 URL → 목록 표를 돌려주는 가짜, 저장은 "이미 저장된 링크" 집합.
 */
class CrawlerServiceTest {

    /** 요청 URL 기록 + URL별 실패 주입 + 서버별 동시 요청 수 측정. */
    private final List<String> requested = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> failingUrls = ConcurrentHashMap.newKeySet();
    private final AtomicLong requestCount = new AtomicLong();

    /** 목록 URL(buildListUrl 결과) → 그 페이지의 글들. */
    private final Map<String, List<ArticleSummary>> pages = new HashMap<>();
    /** 이미 저장된 글 링크. save()가 성공하면 여기 더한다. */
    private final Set<String> stored = ConcurrentHashMap.newKeySet();
    private final List<String> savedLinks = Collections.synchronizedList(new ArrayList<>());

    private CrawlerHttpClient httpClient;
    private NewsCrawlStorageService storage;
    private NewsPushDispatcher pushDispatcher;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        httpClient = mock(CrawlerHttpClient.class);
        when(httpClient.requestCount()).thenAnswer(inv -> requestCount.get());
        when(httpClient.get(anyString())).thenAnswer(inv -> fetch(inv.getArgument(0)));

        storage = mock(NewsCrawlStorageService.class);
        when(storage.findKnown(any(), anyList(), anyBoolean())).thenAnswer(inv -> {
            List<ArticleSummary> summaries = inv.getArgument(1);
            Set<String> known = new HashSet<>();
            for (ArticleSummary s : summaries) {
                if (stored.contains(s.link())) known.add(s.link());
            }
            return new NewsCrawlStorageService.KnownArticles(known, List.of());
        });
        when(storage.save(any(), any(), any())).thenAnswer(inv -> {
            ArticleSummary s = inv.getArgument(1);
            stored.add(s.link());
            savedLinks.add(s.link());
            return Optional.of(News.builder().title(s.title()).sourceUrl(s.link()).build());
        });

        pushDispatcher = mock(NewsPushDispatcher.class);
        clock = new MutableClock(Instant.parse("2026-10-05T00:00:00Z"));
    }

    private Document fetch(String url) {
        requestCount.incrementAndGet();
        requested.add(url);
        if (failingUrls.contains(url)) {
            throw new CrawlerFetchException("HTTP 503 — " + url, null);
        }
        return new Document(url);
    }

    private CrawlerService service(boolean incremental, int parallelServers) {
        CrawlerProperties props = new CrawlerProperties(3, 2, 0, 0, 1000, "test", incremental, parallelServers, 3, 360);
        BoardParserRegistry registry = new BoardParserRegistry(List.of(new FakeParser()));
        return new CrawlerService(httpClient, registry, storage, pushDispatcher, props,
                new CrawlerBoardCircuitBreaker(props, clock));
    }

    private static BoardConfig board(String key, String listUrl) {
        return new BoardConfig(key, key, key, listUrl, null, ParserType.HONGIK, null, false, 100);
    }

    /** page(0부터) 목록에 글 번호들을 최신순으로 넣는다. 링크는 listUrl?view=번호. */
    private void page(BoardConfig board, int page, int... articleNos) {
        List<ArticleSummary> list = new ArrayList<>();
        for (int no : articleNos) {
            list.add(new ArticleSummary(String.valueOf(no), "글 " + no, "", "2026.10.05", null, false,
                    board.listUrl() + "?view=" + no));
        }
        pages.put(board.listUrl() + "#p" + page, list);
    }

    private void store(BoardConfig board, int... articleNos) {
        for (int no : articleNos) stored.add(board.listUrl() + "?view=" + no);
    }

    // ---- 증분 중단 ----

    @Test
    void 첫_페이지가_모두_새_글이면_다음_페이지도_받는다() {
        BoardConfig b = board("a", "https://a.test/list");
        page(b, 0, 10, 9, 8);
        page(b, 1, 7, 6, 5);
        store(b, 6, 5);

        CrawlResult result = service(true, 1).crawlAll(List.of(b));

        assertThat(requested).contains("https://a.test/list#p1");
        assertThat(savedLinks).containsExactly(
                "https://a.test/list?view=10", "https://a.test/list?view=9", "https://a.test/list?view=8",
                "https://a.test/list?view=7");
        assertThat(result.savedCount()).isEqualTo(4);
    }

    @Test
    void 페이지의_가장_오래된_글이_이미_저장돼_있으면_다음_페이지는_요청하지_않는다() {
        BoardConfig b = board("a", "https://a.test/list");
        page(b, 0, 10, 9, 8);
        page(b, 1, 7, 6, 5);
        store(b, 9, 8, 7, 6, 5);

        CrawlResult result = service(true, 1).crawlAll(List.of(b));

        // 목록 1 + 새 글(10) 상세 1 = 2회. 2페이지·저장된 글(9, 8) 상세는 요청하지 않는다.
        assertThat(requested).containsExactly("https://a.test/list#p0", "https://a.test/list?view=10");
        assertThat(result.requestCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(1);
    }

    @Test
    void 상단_고정_공지처럼_맨_위만_저장된_글이면_계속_다음_페이지를_받는다() {
        BoardConfig b = board("a", "https://a.test/list");
        page(b, 0, 1, 10, 9);   // 1 = 오래된 고정 공지(이미 저장), 그 아래는 새 글
        page(b, 1, 1, 8, 7);
        store(b, 1, 7);

        service(true, 1).crawlAll(List.of(b));

        assertThat(requested).contains("https://a.test/list#p1");
        assertThat(savedLinks).containsExactly(
                "https://a.test/list?view=10", "https://a.test/list?view=9", "https://a.test/list?view=8");
    }

    @Test
    void 증분_수집을_끄면_예전처럼_모든_페이지를_받되_저장된_글_상세는_받지_않는다() {
        BoardConfig b = board("a", "https://a.test/list");
        page(b, 0, 10, 9, 8);
        page(b, 1, 7, 6, 5);
        store(b, 10, 9, 8, 7, 6, 5);

        service(false, 1).crawlAll(List.of(b));

        assertThat(requested).containsExactly("https://a.test/list#p0", "https://a.test/list#p1");
        verify(storage, never()).save(any(), any(), any());
    }

    @Test
    void 이미_저장된_글_판단은_페이지당_한_번이고_글마다_조회하지_않는다() {
        BoardConfig b = board("a", "https://a.test/list");
        page(b, 0, 10, 9, 8);
        page(b, 1, 7, 6, 5);

        service(true, 1).crawlAll(List.of(b));

        verify(storage, org.mockito.Mockito.times(2)).findKnown(any(), anyList(), anyBoolean());
        verify(storage, never()).alreadyExists(any(), any(), anyBoolean());
    }

    @Test
    void 이전_실행이_중간에_실패했으면_다음_실행은_증분_중단_없이_끝까지_본다() {
        BoardConfig b = board("a", "https://a.test/list");
        page(b, 0, 10, 9, 8);
        page(b, 1, 7, 6, 5);
        failingUrls.add("https://a.test/list#p1");
        CrawlerService service = service(true, 1);

        CrawlResult first = service.crawlAll(List.of(b));
        assertThat(first.failedBoards()).containsExactly("a");
        assertThat(first.savedCount()).isEqualTo(3); // 실패 전까지 저장한 글은 푸시 대상에 남는다

        failingUrls.clear();
        requested.clear();
        service.crawlAll(List.of(b));
        // 1페이지가 전부 저장된 상태지만 지난번에 못 받은 2페이지를 받는다.
        assertThat(requested).contains("https://a.test/list#p1");
        assertThat(stored).contains("https://a.test/list?view=7", "https://a.test/list?view=5");

        requested.clear();
        service.crawlAll(List.of(b));
        assertThat(requested).containsExactly("https://a.test/list#p0"); // 정상화 뒤엔 다시 증분
    }

    // ---- 연속 실패 건너뛰기 ----

    @Test
    void 연속_실패한_게시판은_쿨다운_동안_건너뛰고_지나면_다시_시도해_정상화한다() {
        BoardConfig dead = board("dead", "https://dead.test/list");
        BoardConfig alive = board("alive", "https://alive.test/list");
        page(alive, 0, 1);
        failingUrls.add("https://dead.test/list#p0");
        CrawlerService service = service(true, 1);

        for (int run = 0; run < 3; run++) {
            assertThat(service.crawlAll(List.of(dead, alive)).failedBoards()).containsExactly("dead");
        }

        requested.clear();
        CrawlResult skippedRun = service.crawlAll(List.of(dead, alive));
        assertThat(skippedRun.skippedBoards()).containsExactly("dead");
        assertThat(skippedRun.failedBoards()).isEmpty();
        assertThat(requested).noneMatch(url -> url.startsWith("https://dead.test"));
        assertThat(requested).contains("https://alive.test/list#p0");

        clock.advance(Duration.ofMinutes(361));
        failingUrls.clear();
        page(dead, 0, 1);
        requested.clear();
        CrawlResult recovered = service.crawlAll(List.of(dead, alive));
        assertThat(recovered.skippedBoards()).isEmpty();
        assertThat(requested).contains("https://dead.test/list#p0");
    }

    // ---- 서버별 순차 병렬 ----

    @Test
    void 서로_다른_서버끼리만_동시에_돌고_같은_서버에는_한_번에_한_요청만_보낸다() throws Exception {
        // a1/a2 → 서버 A, b1/b2 → 서버 B (호스트명은 달라도 IP가 같은 학교 서브도메인 상황)
        List<BoardConfig> boards = List.of(
                board("a1", "https://a1.test/list"), board("b1", "https://b1.test/list"),
                board("a2", "https://a2.test/list"), board("b2", "https://b2.test/list"));
        for (BoardConfig b : boards) page(b, 0, 2, 1);

        Map<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();
        Map<String, Integer> maxInFlight = new ConcurrentHashMap<>();
        CountDownLatch bothServersStarted = new CountDownLatch(2);
        Set<String> startedServers = ConcurrentHashMap.newKeySet();
        AtomicInteger notParallel = new AtomicInteger();
        Function<String, String> serverOf = host -> host.substring(0, 1).toUpperCase();

        doAnswer(inv -> {
            String url = inv.getArgument(0);
            String server = serverOf.apply(URI.create(url).getHost());
            int now = inFlight.computeIfAbsent(server, k -> new AtomicInteger()).incrementAndGet();
            maxInFlight.merge(server, now, Math::max);
            try {
                if (startedServers.add(server)) {
                    bothServersStarted.countDown();
                    // 다른 서버 묶음이 동시에 시작하지 않으면(=순차 실행) 여기서 시간 초과된다.
                    if (!bothServersStarted.await(2, TimeUnit.SECONDS)) notParallel.incrementAndGet();
                }
                Thread.sleep(5);
                return fetch(url);
            } finally {
                inFlight.get(server).decrementAndGet();
            }
        }).when(httpClient).get(anyString());

        CrawlerService service = service(true, 2);
        service.serverResolver = serverOf;
        CrawlResult result = service.crawlAll(boards);

        assertThat(notParallel.get()).isZero();
        assertThat(maxInFlight).containsEntry("A", 1).containsEntry("B", 1);
        assertThat(result.savedCount()).isEqualTo(8);
        assertThat(result.failedBoards()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 병렬로_돌아도_새_소식은_게시판_설정_순서대로_푸시한다() {
        List<BoardConfig> boards = List.of(
                board("a1", "https://a1.test/list"), board("b1", "https://b1.test/list"),
                board("a2", "https://a2.test/list"));
        for (BoardConfig b : boards) page(b, 0, 1);
        CrawlerService service = service(true, 2);
        service.serverResolver = host -> host.substring(0, 1);

        service.crawlAll(boards);

        org.mockito.ArgumentCaptor<List<News>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(pushDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue()).extracting(News::getSourceUrl).containsExactly(
                "https://a1.test/list?view=1", "https://b1.test/list?view=1", "https://a2.test/list?view=1");
    }

    // ---- 가짜 파서 / 시계 ----

    private class FakeParser implements BoardParser {
        @Override
        public ParserType type() {
            return ParserType.HONGIK;
        }

        @Override
        public String buildListUrl(String listUrl, int page, int pageSize) {
            return listUrl + "#p" + page;
        }

        @Override
        public List<ArticleSummary> parseList(Document document, String listUrl, String tableSummary) {
            return pages.getOrDefault(document.location(), List.of());
        }

        @Override
        public ArticleDetail parseView(Document document, String pageUrl) {
            return null;
        }
    }

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
