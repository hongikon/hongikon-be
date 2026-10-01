package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.parser.ArticleDetail;
import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
import com.hongmap.hongmapbackend.department.Department;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsAttachment;
import com.hongmap.hongmapbackend.news.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /**
     * news.source_url UNIQUE 제약을 그대로 중복 판단 기준으로 쓴다(=크롤러의 knownIds 역할).
     * 단 상세 링크가 매번 바뀌는 게시판(stableUrl=false, 건축학부)은 링크로는 판단할 수 없어
     * 게시판 출처+제목+작성일로 판단한다.
     */
    @Transactional(readOnly = true)
    public boolean alreadyExists(BoardConfig board, ArticleSummary summary, boolean stableUrl) {
        if (stableUrl) {
            return newsRepository.existsBySourceUrl(summary.link());
        }
        LocalDateTime publishedAt = parseDate(summary.date());
        return publishedAt != null
                ? newsRepository.existsBySourceIdAndTitleAndPublishedAt(board.sourceId(), summary.title(), publishedAt)
                : newsRepository.existsBySourceIdAndTitle(board.sourceId(), summary.title());
    }

    /**
     * 이미 저장된 글들(sourceUrls)의 source_id가 비어 있으면 이 게시판의 sourceId로 채운다.
     * source_id 컬럼 도입 전에 저장된 글을 시간당 크롤링이 다시 만날 때 자연스럽게 메우기 위함이다.
     * 이미 값이 있는 행은 건드리지 않는다.
     */
    @Transactional
    public int fillMissingSourceId(BoardConfig board, Collection<String> sourceUrls) {
        if (sourceUrls == null || sourceUrls.isEmpty()) {
            return 0;
        }
        return newsRepository.fillMissingSourceId(board.sourceId(), sourceUrls);
    }

    /** 새로 저장한 News를 돌려준다(푸시 발송용). 이미 저장된 글이면 저장하지 않고 빈 값을 돌려준다. */
    @Transactional
    public Optional<News> save(BoardConfig board, ArticleSummary summary, ArticleDetail detail) {
        if (newsRepository.existsBySourceUrl(summary.link())) {
            return Optional.empty();
        }

        String content = detail != null ? detail.content() : null;
        Department department = locationMatcher.matchDepartment(board);
        Building building = locationMatcher.matchBuilding(summary.title(), content);

        News news = News.builder()
                .title(summary.title())
                .content(content)
                .images(detail != null && detail.images() != null ? detail.images() : List.of())
                .attachments(toNewsAttachments(detail))
                .views(detail != null ? detail.views() : null)
                .category(NewsCategoryClassifier.classify(summary.title()))
                .sourceUrl(summary.link())
                .sourceId(board.sourceId())
                .department(department)
                .building(building)
                .publishedAt(resolvePublishedAt(summary, detail))
                .build();

        return Optional.of(newsRepository.save(news));
    }

    /** 크롤러의 Attachment(파싱 결과)를 news 도메인의 NewsAttachment(저장용)로 옮긴다. */
    private List<NewsAttachment> toNewsAttachments(ArticleDetail detail) {
        if (detail == null || detail.attachments() == null) return List.of();
        return detail.attachments().stream()
                .map(a -> new NewsAttachment(a.name(), a.url()))
                .toList();
    }

    private LocalDateTime resolvePublishedAt(ArticleSummary summary, ArticleDetail detail) {
        String raw = !summary.date().isBlank() ? summary.date() : (detail != null ? detail.date() : "");
        LocalDateTime parsed = parseDate(raw);
        // 날짜를 못 읽은 경우까지 저장을 막을 정도는 아니라고 판단, 크롤링 시각으로 대체한다.
        return parsed != null ? parsed : LocalDateTime.now();
    }

    /** "yyyy.MM.dd" → 그날 0시. 비어 있거나 형식이 다르면 null. */
    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw, DATE_FORMAT).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
