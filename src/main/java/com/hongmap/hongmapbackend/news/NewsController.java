package com.hongmap.hongmapbackend.news;

import com.hongmap.hongmapbackend.news.dto.NewsListResponse;
import com.hongmap.hongmapbackend.news.dto.NewsResponse;
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

    @GetMapping("/news")
    public NewsListResponse getFiltered(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long buildingId
    ) {
        return newsService.getFiltered(category, departmentId, buildingId);
    }

    @GetMapping("/news/{id}")
    public NewsResponse getById(@PathVariable Long id) {
        return newsService.getById(id);
    }
}
