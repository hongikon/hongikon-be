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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
     * 목록 한 페이지에서 이미 저장된 글 판단을 한 번에 한다.
     *
     * @param knownLinks             이미 저장된 글의 상세 링크(summary.link()) — 상세 요청·저장을 건너뛸 대상
     * @param linksMissingSourceId   그중 source_id가 비어 있는 것 — {@link #fillMissingSourceId}로 채울 대상
     */
    public record KnownArticles(Set<String> knownLinks, List<String> linksMissingSourceId) {

        public boolean isKnown(ArticleSummary summary) {
            return knownLinks.contains(summary.link());
        }
    }

    /**
     * {@link #alreadyExists}의 페이지 단위 버전. 링크가 고정인 게시판은 source_url IN (...) 한 번으로 끝낸다
     * (예전: 글마다 exists 1회 + 페이지마다 UPDATE 1회 → 지금: 페이지당 SELECT 1회, UPDATE는 채울 행이 있을 때만).
     * 링크가 매번 바뀌는 게시판(건축학부)은 제목·작성일 비교를 DB 콜레이션에 맡기려고 기존 글별 판단을 그대로 쓴다
     * — 두 게시판·페이지당 십여 건이라 비용이 작고, 자바 쪽 문자열 비교로 바꾸면 대소문자·공백 차이로 중복 저장될 수 있다.
     */
    @Transactional(readOnly = true)
    public KnownArticles findKnown(BoardConfig board, List<ArticleSummary> summaries, boolean stableUrl) {
        if (summaries.isEmpty()) {
            return new KnownArticles(Set.of(), List.of());
        }
        if (!stableUrl) {
            Set<String> known = new HashSet<>();
            for (ArticleSummary summary : summaries) {
                if (alreadyExists(board, summary, false)) {
                    known.add(summary.link());
                }
            }
            // 링크가 매번 바뀌어 이번 링크는 DB에 없다 — source_id 채우기 대상이 없다(기존 동작과 같음).
            return new KnownArticles(known, List.of());
        }

        List<String> links = summaries.stream().map(ArticleSummary::link).distinct().toList();
        Set<String> known = new HashSet<>();
        List<String> missingSourceId = new ArrayList<>();
        for (NewsRepository.SourceUrlState state : newsRepository.findSourceUrlStates(links)) {
            known.add(state.getSourceUrl());
            if (state.getSourceId() == null) {
                missingSourceId.add(state.getSourceUrl());
            }
        }
        return new KnownArticles(known, missingSourceId);
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
                .category(NewsCategoryClassifier.classify(summary.title(), content, board.sourceId()))
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
