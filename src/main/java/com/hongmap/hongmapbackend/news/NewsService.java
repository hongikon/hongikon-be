package com.hongmap.hongmapbackend.news;

import com.hongmap.hongmapbackend.news.dto.NewsListResponse;
import com.hongmap.hongmapbackend.news.dto.NewsResponse;
import com.hongmap.hongmapbackend.news.dto.NewsSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * "구독 소식" 필터링(user_departments + notification_categories 기준)은
 * Department 도메인 완성 후 추가 예정. 지금은 카테고리/학과ID/건물ID 직접 필터만 지원.
 */
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository newsRepository;

    @Transactional(readOnly = true)
    public NewsListResponse getFiltered(String category, Long departmentId, Long buildingId) {
        var news = newsRepository.findFiltered(category, departmentId, buildingId).stream()
                .map(NewsSummaryResponse::of)
                .toList();
        return new NewsListResponse(news);
    }

    @Transactional(readOnly = true)
    public NewsResponse getById(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 소식입니다."));
        return NewsResponse.of(news);
    }
}
