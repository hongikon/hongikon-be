package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 물어보기 답 쓰기 도구 — 학교 공지 찾기·읽기(실제 DB, H2). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AskLiveDataTest {

    @Autowired AskLiveData liveData;
    @Autowired NewsRepository newsRepository;

    private News save(String title, String content, LocalDateTime publishedAt) {
        return newsRepository.save(News.builder()
                .title(title).content(content).category("공지")
                .sourceUrl("https://example.com/" + UUID.randomUUID())
                .publishedAt(publishedAt)
                .build());
    }

    @Test
    void 제목에_맞는_낱말이_많은_공지부터_같으면_최신순으로_찾는다() {
        String tag = "물어보기테스트" + UUID.randomUUID().toString().substring(0, 6);
        News older = save(tag + " 2026학년도 1학기 휴학 신청 안내", "본문", LocalDateTime.of(2026, 1, 20, 0, 0));
        News newer = save(tag + " 2026학년도 2학기 휴학 신청 안내", "본문", LocalDateTime.of(2026, 7, 25, 0, 0));
        News course = save(tag + " 2학기 수강신청 일정", "본문", LocalDateTime.of(2026, 8, 1, 0, 0));

        // 휴학 공지 둘은 세 낱말이 다 맞고(최신순), 수강신청 공지는 두 낱말만 맞아 가장 새것이어도 뒤에
        assertThat(liveData.searchNotices(tag + " 휴학 신청"))
                .extracting(AskLiveData.Reference::ref)
                .containsExactly("notice:" + newer.getId(), "notice:" + older.getId(), "notice:" + course.getId());
        // 맞는 낱말이 적어도(태그만) 나오되, 많이 맞은 글이 먼저
        assertThat(liveData.searchNotices(tag + " 휴학 존재하지않는낱말"))
                .extracting(AskLiveData.Reference::ref)
                .startsWith("notice:" + newer.getId(), "notice:" + older.getId())
                .hasSize(3);
        assertThat(liveData.searchNotices("가")).isEmpty();
    }

    @Test
    void 학생_말과_공지_말이_달라도_같은_뜻_낱말로_찾는다() {
        String tag = "물어보기동의어" + UUID.randomUUID().toString().substring(0, 6);
        News outbound = save(tag + " 2027학년도 1학기 해외대학 파견 Outbound 교류학생 지원자 모집", "본문", LocalDateTime.of(2026, 7, 6, 0, 0));
        assertThat(liveData.searchNotices(tag + " 교환학생 모집"))
                .extracting(AskLiveData.Reference::ref)
                .first().isEqualTo("notice:" + outbound.getId());
    }

    @Test
    void 공지_본문은_태그를_지우고_길면_자른다() {
        News news = save("본문 테스트 공지", "<p>신청 기간은 <b>7월 28일</b>까지</p>" + "가".repeat(7000), LocalDateTime.of(2026, 7, 1, 0, 0));
        AskLiveData.NoticeBody body = liveData.readNotice(news.getId()).orElseThrow();
        assertThat(body.content()).startsWith("신청 기간은 7월 28일 까지").endsWith("(이하 생략)");
        assertThat(body.reference().date()).isEqualTo("2026-07-01");
        assertThat(liveData.readNotice(-1)).isEmpty();
    }

    @Test
    void 시설_층은_지하_로비층_지상으로_쓴다() {
        assertThat(AskLiveData.floorLabel(-2)).isEqualTo("지하 2층");
        assertThat(AskLiveData.floorLabel(0)).isEqualTo("로비층(L층)");
        assertThat(AskLiveData.floorLabel(3)).isEqualTo("3층");
    }
}
