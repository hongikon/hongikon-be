package com.hongmap.hongmapbackend.news.dto;

import com.hongmap.hongmapbackend.news.News;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record NewsResponse(
        Long id,
        String title,
        String content,
        String category,
        String sourceUrl,
        Long departmentId,
        Long buildingId,
        LocalDateTime publishedAt
) {
    public static NewsResponse of(News n) {
        return NewsResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .content(n.getContent())
                .category(n.getCategory())
                .sourceUrl(n.getSourceUrl())
                .departmentId(n.getDepartmentId())
                .buildingId(n.getBuilding() != null ? n.getBuilding().getId() : null)
                .publishedAt(n.getPublishedAt())
                .build();
    }
}
