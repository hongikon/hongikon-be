package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
}
