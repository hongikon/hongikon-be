package com.hongmap.hongmapbackend.news.dto;

import com.hongmap.hongmapbackend.news.News;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record NewsSummaryResponse(
        Long id,
        String title,
        String category,
        Long departmentId,
        Long buildingId,
        LocalDateTime publishedAt
) {
    public static NewsSummaryResponse of(News n) {
        return NewsSummaryResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .category(n.getCategory())
                .departmentId(n.getDepartmentId())
                .buildingId(n.getBuilding() != null ? n.getBuilding().getId() : null)
                .publishedAt(n.getPublishedAt())
                .build();
    }
}
