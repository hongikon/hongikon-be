package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 크롤러 중복 판단. 건축학부는 같은 글의 상세 링크(idx·code)가 요청마다 달라
 * 링크 기준으로는 매 크롤링마다 새 글로 저장되던 버그의 회귀 테스트. H2 인메모리 DB(application-test.properties).
 */
@SpringBootTest
@ActiveProfiles("test")
class NewsCrawlStorageServiceTest {

    private static final String ARCH_EVENT = "https://arch.hongik.ac.kr/kor/news/event.php";

    @Autowired NewsCrawlStorageService storageService;

    private final BoardConfig archBoard = CrawlerBoards.ALL.stream()
            .filter(b -> b.boardKey().equals("arch-ev"))
            .findFirst()
            .orElseThrow();

    private ArticleSummary summary(String title, String date) {
        String token = UUID.randomUUID().toString();
        return new ArticleSummary(token, title, "", date, null, false,
                ARCH_EVENT + "?m=v&idx=" + token + "&pNo=1&code=" + UUID.randomUUID());
    }

    @Test
    void 링크가_매번_바뀌는_게시판은_제목과_작성일로_이미_저장된_글을_알아본다() {
        String title = "AAVS Korea 2026: Encoded Heritage 결과물 전시 " + UUID.randomUUID();
        assertThat(storageService.save(archBoard, summary(title, "2026.08.10"), null)).isPresent();

        ArticleSummary recrawled = summary(title, "2026.08.10");

        // 링크 기준(기존 동작)으로는 같은 글을 못 알아봐 중복 저장됐다.
        assertThat(storageService.alreadyExists(archBoard, recrawled, true)).isFalse();
        assertThat(storageService.alreadyExists(archBoard, recrawled, false)).isTrue();
    }

    @Test
    void 제목이나_작성일이_다르면_다른_글로_본다() {
        String title = "AAVS 국제 학생 워크숍 " + UUID.randomUUID();
        storageService.save(archBoard, summary(title, "2026.08.10"), null);

        assertThat(storageService.alreadyExists(archBoard, summary(title, "2026.08.11"), false)).isFalse();
        assertThat(storageService.alreadyExists(archBoard, summary(title + " (2차)", "2026.08.10"), false)).isFalse();
    }

    @Test
    void 목록에서_작성일을_못_읽으면_제목만으로_판단한다() {
        String title = "날짜 없는 글 " + UUID.randomUUID();
        storageService.save(archBoard, summary(title, ""), null);

        assertThat(storageService.alreadyExists(archBoard, summary(title, ""), false)).isTrue();
    }

    // ---- 페이지 단위 중복 판단(findKnown) ----

    private final BoardConfig ceBoard = CrawlerBoards.ALL.stream()
            .filter(b -> b.boardKey().equals("ce"))
            .findFirst()
            .orElseThrow();

    @Autowired com.hongmap.hongmapbackend.news.NewsRepository newsRepository;

    private ArticleSummary ceSummary(String title) {
        String no = UUID.randomUUID().toString();
        return new ArticleSummary(no, title, "", "2026.10.05", null, false,
                "https://wwwce.hongik.ac.kr/wwwce/0401.do?mode=view&articleNo=" + no);
    }

    @Test
    void 링크가_고정인_게시판은_한_번의_조회로_저장된_글과_source_id_빈_글을_가려낸다() {
        ArticleSummary saved = ceSummary("저장된 글");
        ArticleSummary legacy = ceSummary("source_id 없던 시절 글");
        ArticleSummary fresh = ceSummary("새 글");
        storageService.save(ceBoard, saved, null);
        newsRepository.save(com.hongmap.hongmapbackend.news.News.builder()
                .title(legacy.title()).category("학사").sourceUrl(legacy.link())
                .publishedAt(java.time.LocalDateTime.now()).build());

        NewsCrawlStorageService.KnownArticles known =
                storageService.findKnown(ceBoard, List.of(fresh, saved, legacy), true);

        assertThat(known.isKnown(saved)).isTrue();
        assertThat(known.isKnown(legacy)).isTrue();
        assertThat(known.isKnown(fresh)).isFalse();
        assertThat(known.linksMissingSourceId()).containsExactly(legacy.link());

        // 채운 뒤에는 더 이상 채울 대상이 아니다(평시 UPDATE 0회).
        storageService.fillMissingSourceId(ceBoard, known.linksMissingSourceId());
        assertThat(storageService.findKnown(ceBoard, List.of(legacy), true).linksMissingSourceId()).isEmpty();
    }

    @Test
    void 링크가_매번_바뀌는_게시판은_제목과_작성일로_페이지_단위_판단을_한다() {
        String title = "건축 행사 " + UUID.randomUUID();
        storageService.save(archBoard, summary(title, "2026.10.01"), null);

        ArticleSummary recrawled = summary(title, "2026.10.01");
        ArticleSummary other = summary(title + " 2", "2026.10.01");
        NewsCrawlStorageService.KnownArticles known =
                storageService.findKnown(archBoard, List.of(recrawled, other), false);

        assertThat(known.isKnown(recrawled)).isTrue();
        assertThat(known.isKnown(other)).isFalse();
        assertThat(known.linksMissingSourceId()).isEmpty();
    }
    // ---- 상단 고정 공지(국제협력본부) ----

    private final BoardConfig oiaBoard = CrawlerBoards.ALL.stream()
            .filter(b -> b.boardKey().equals("oia-exchange"))
            .findFirst()
            .orElseThrow();

    private ArticleSummary oiaSummary(boolean pinned) {
        String no = UUID.randomUUID().toString();
        return new ArticleSummary(no, "국제교류실 글 " + no, "", "2026.10.12", null, false,
                "https://inno.hongik.ac.kr/oia/boardview/1/" + no, pinned);
    }

    private boolean pinnedOf(String sourceUrl) {
        return newsRepository.findAll().stream()
                .filter(n -> n.getSourceUrl().equals(sourceUrl))
                .findFirst()
                .orElseThrow()
                .isPinned();
    }

    @Test
    void 고정_목록으로_새로_고정된_글은_켜고_풀린_글은_끈다() {
        ArticleSummary wasPinned = oiaSummary(true);
        ArticleSummary stillPinned = oiaSummary(true);
        ArticleSummary nowPinned = oiaSummary(false);
        storageService.save(oiaBoard, wasPinned, null);
        storageService.save(oiaBoard, stillPinned, null);
        storageService.save(oiaBoard, nowPinned, null);
        assertThat(pinnedOf(wasPinned.link())).isTrue();
        assertThat(pinnedOf(nowPinned.link())).isFalse();

        storageService.syncPinned(oiaBoard, List.of(stillPinned.link(), nowPinned.link()));

        assertThat(pinnedOf(wasPinned.link())).isFalse();
        assertThat(pinnedOf(stillPinned.link())).isTrue();
        assertThat(pinnedOf(nowPinned.link())).isTrue();

        // 고정 공지가 모두 내려가면 이 게시판 글은 전부 고정 아님.
        storageService.syncPinned(oiaBoard, List.of());
        assertThat(pinnedOf(stillPinned.link())).isFalse();
        assertThat(pinnedOf(nowPinned.link())).isFalse();
    }

    @Test
    void 고정_여부_필터는_고정_글만_또는_고정_글을_뺀_목록을_준다() {
        ArticleSummary pinned = oiaSummary(true);
        ArticleSummary normal = oiaSummary(false);
        storageService.save(oiaBoard, pinned, null);
        storageService.save(oiaBoard, normal, null);

        List<String> onlyPinned = newsRepository.findAll(
                        new NewsFilter(null, null, null, List.of("국제교류실"), null, true).toSpecification())
                .stream().map(News::getSourceUrl).toList();
        List<String> withoutPinned = newsRepository.findAll(
                        new NewsFilter(null, null, null, List.of("국제교류실"), null, false).toSpecification())
                .stream().map(News::getSourceUrl).toList();

        assertThat(onlyPinned).contains(pinned.link()).doesNotContain(normal.link());
        assertThat(withoutPinned).contains(normal.link()).doesNotContain(pinned.link());
    }
}
