package com.hongmap.hongmapbackend.news;

import com.hongmap.hongmapbackend.common.dto.PageResponse;
import com.hongmap.hongmapbackend.news.dto.NewsResponse;
import com.hongmap.hongmapbackend.news.dto.NewsSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    /**
     * 최신순(작성일 내림차순) 고정 — 클라이언트가 보낸 sort는 버리고 page/size만 쓴다.
     * 작성일은 날짜만 있어(0시) 같은 값이 많으므로 id를 보조 정렬로 둬 페이지 경계에서 글이 겹치거나 빠지지 않게 한다.
     */
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    @Transactional(readOnly = true)
    public PageResponse<NewsSummaryResponse> getFiltered(NewsFilter filter, Pageable pageable) {
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_FIRST);
        return PageResponse.of(newsRepository.findAll(filter.toSpecification(), latestFirst)
                .map(NewsSummaryResponse::of));
    }

    @Transactional(readOnly = true)
    public NewsResponse getById(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 소식입니다."));
        return NewsResponse.of(news);
    }
}
