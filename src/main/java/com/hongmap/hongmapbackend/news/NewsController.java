package com.hongmap.hongmapbackend.news;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.news.dto.NewsListResponse;
import com.hongmap.hongmapbackend.news.dto.NewsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * README 원칙("지도·공지는 비로그인 가능") — 게스트 허용.
 * SecurityConfig의 permitAll()에 GET /news, GET /news/** 등록 필요.
 */
@RestController
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "공지사항 목록 조회", description = "카테고리, 학과, 건물로 필터링된 공지사항 목록을 조회합니다.")
    @GetMapping("/news")
    public NewsListResponse getFiltered(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long buildingId
    ) {
        return newsService.getFiltered(category, departmentId, buildingId);
    }

    @Tag(name = SwaggerConfig.TAG_NEWS_NOTIFICATION)
    @Operation(summary = "공지사항 상세 조회", description = "공지사항 id로 특정 공지사항의 상세 내용을 조회합니다.")
    @GetMapping("/news/{id}")
    public NewsResponse getById(@PathVariable Long id) {
        return newsService.getById(id);
    }
}
