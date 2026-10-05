package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
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
}
