package com.hongmap.hongmapbackend.news.dto;

import java.util.List;

public record NewsListResponse(
        List<NewsSummaryResponse> news
) {
}
