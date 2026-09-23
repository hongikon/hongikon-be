package com.hongmap.hongmapbackend.news.dto;

import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsAttachment;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record NewsResponse(
        Long id,
        String title,
        String content,
        List<String> images,
        List<NewsAttachment> attachments,
        Integer views,
        String category,
        String sourceUrl,
        Long departmentId,
        String departmentName,
        Long buildingId,
        LocalDateTime publishedAt
) {
    public static NewsResponse of(News n) {
        return NewsResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .content(n.getContent())
                .images(n.getImages())
                .attachments(n.getAttachments())
                .views(n.getViews())
                .category(n.getCategory())
                .sourceUrl(n.getSourceUrl())
                .departmentId(n.getDepartment() != null ? n.getDepartment().getId() : null)
                .departmentName(n.getDepartment() != null ? n.getDepartment().getName() : null)
                .buildingId(n.getBuilding() != null ? n.getBuilding().getId() : null)
                .publishedAt(n.getPublishedAt())
                .build();
    }
}
