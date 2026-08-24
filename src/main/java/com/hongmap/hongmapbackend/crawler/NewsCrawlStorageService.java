package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.parser.ArticleDetail;
import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
import com.hongmap.hongmapbackend.department.Department;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 크롤링한 게시글 하나를 News 엔티티로 변환해 저장하는 책임만 진다.
 * 크롤링(HTTP+파싱)은 CrawlerHttpClient/BoardParser, 분류는 NewsCategoryClassifier가 맡고
 * 이 클래스는 그 결과를 받아 기존 News 엔티티/Repository 구조에 맞춰 저장만 한다.
 */
@Service
@RequiredArgsConstructor
public class NewsCrawlStorageService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final NewsRepository newsRepository;
    private final NewsLocationMatcher locationMatcher;

    /** news.source_url UNIQUE 제약을 그대로 중복 판단 기준으로 쓴다(=크롤러의 knownIds 역할). */
    @Transactional(readOnly = true)
    public boolean alreadyExists(String sourceUrl) {
        return newsRepository.existsBySourceUrl(sourceUrl);
    }

    /** 이미 저장된 글이면 저장하지 않고 false를 돌려준다. */
    @Transactional
    public boolean save(BoardConfig board, ArticleSummary summary, ArticleDetail detail) {
        if (newsRepository.existsBySourceUrl(summary.link())) {
            return false;
        }

        String content = detail != null ? detail.content() : null;
        Department department = locationMatcher.matchDepartment(board);
        Building building = locationMatcher.matchBuilding(summary.title(), content);

        News news = News.builder()
                .title(summary.title())
                .content(content)
                .category(NewsCategoryClassifier.classify(summary.title()))
                .sourceUrl(summary.link())
                .department(department)
                .building(building)
                .publishedAt(resolvePublishedAt(summary, detail))
                .build();

        newsRepository.save(news);
        return true;
    }

    private LocalDateTime resolvePublishedAt(ArticleSummary summary, ArticleDetail detail) {
        String raw = !summary.date().isBlank() ? summary.date() : (detail != null ? detail.date() : "");
        if (raw == null || raw.isBlank()) {
            // 날짜를 못 읽은 경우까지 저장을 막을 정도는 아니라고 판단, 크롤링 시각으로 대체한다.
            return LocalDateTime.now();
        }

        try {
            return LocalDate.parse(raw, DATE_FORMAT).atStartOfDay();
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }
}
