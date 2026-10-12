package com.hongmap.hongmapbackend.news;

import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * GET /news 목록 필터. 값이 비어 있는 조건은 적용하지 않고, 주어진 조건끼리는 AND로 조합한다.
 *
 * @param category     카테고리 일치
 * @param departmentId 학과 id 일치
 * @param buildingId   건물 id 일치
 * @param sourceIds    수집 게시판 id(news.source_id) 중 하나와 일치(IN). 프론트 구독 필터링용 — 예: ["학사", "장학"]
 *                     자체 게시판이 없는 별칭(예: 데이터사이언스전공)은 상위 게시판 id로 바꿔 찾는다(CrawlerBoards.SOURCE_ALIASES).
 * @param keyword      제목 부분 일치(LIKE %keyword%). %, _ 는 와일드카드가 아닌 글자 그대로 찾는다.
 * @param pinned       상단 고정 공지 여부. true면 고정 글만, false면 고정 글을 뺀 목록, null이면 구분하지 않는다.
 *                     게시판 화면이 고정 공지를 목록 위에 따로 묶어 보여 주고 본 목록에서는 빼기 위해 쓴다.
 */
public record NewsFilter(
        String category,
        Long departmentId,
        Long buildingId,
        List<String> sourceIds,
        String keyword,
        Boolean pinned
) {
    /** 백슬래시는 MySQL 문자열 리터럴에서도 이스케이프로 해석돼 헷갈리므로 LIKE 이스케이프 문자로 '!'를 쓴다. */
    private static final char LIKE_ESCAPE = '!';

    public NewsFilter {
        sourceIds = sourceIds == null ? List.of() : CrawlerBoards.resolveStoredSourceIds(sourceIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .toList());
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public NewsFilter(String category, Long departmentId, Long buildingId, List<String> sourceIds, String keyword) {
        this(category, departmentId, buildingId, sourceIds, keyword, null);
    }

    public Specification<News> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (buildingId != null) {
                predicates.add(cb.equal(root.get("building").get("id"), buildingId));
            }
            if (!sourceIds.isEmpty()) {
                predicates.add(root.get("sourceId").in(sourceIds));
            }
            if (keyword != null) {
                predicates.add(cb.like(root.get("title"), "%" + escapeLike(keyword) + "%", LIKE_ESCAPE));
            }
            if (pinned != null) {
                predicates.add(cb.equal(root.get("pinned"), pinned));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String raw) {
        return raw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
