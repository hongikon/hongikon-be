package com.hongmap.hongmapbackend.news;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.common.dto.PageResponse;
import com.hongmap.hongmapbackend.news.dto.NewsResponse;
import com.hongmap.hongmapbackend.news.dto.NewsSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * README 원칙("지도·공지는 비로그인 가능") — 게스트 허용.
 * SecurityConfig의 permitAll()에 GET /news, GET /news/** 등록 필요.
 */
@RestController
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "공지사항 목록 조회", description = "카테고리, 학과, 건물, 수집 게시판(sourceId), 제목 키워드로 필터링된 공지사항 목록을 "
            + "최신순으로 페이지 단위 조회합니다. 조건끼리는 AND로 조합되고, sourceId는 여러 개 주면 그중 하나에 해당하는 글(sourceId=학사&sourceId=장학). "
            + "keyword는 제목 부분 일치. page는 0부터, size는 기본 20·최대 50(넘으면 50으로 맞춤). sort는 무시되고 항상 작성일 최신순입니다.")
    @GetMapping("/news")
    public PageResponse<NewsSummaryResponse> getFiltered(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) List<String> sourceId,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        return newsService.getFiltered(new NewsFilter(category, departmentId, buildingId, sourceId, keyword), pageable);
    }

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "공지사항 상세 조회", description = "공지사항 id로 특정 공지사항의 상세 내용을 조회합니다.")
    @GetMapping("/news/{id}")
    public NewsResponse getById(@PathVariable Long id) {
        return newsService.getById(id);
    }
}
