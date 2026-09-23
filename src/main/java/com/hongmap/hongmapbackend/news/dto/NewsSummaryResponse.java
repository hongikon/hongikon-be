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
        /** 출처 표시명(예: "컴퓨터공학과"). department가 아직 안 붙은 소식은 null — 프론트가 폴백 문구를 쓴다. */
        String departmentName,
        Long buildingId,
        LocalDateTime publishedAt
) {
    public static NewsSummaryResponse of(News n) {
        return NewsSummaryResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .category(n.getCategory())
                .departmentId(n.getDepartment() != null ? n.getDepartment().getId() : null)
                .departmentName(n.getDepartment() != null ? n.getDepartment().getName() : null)
                .buildingId(n.getBuilding() != null ? n.getBuilding().getId() : null)
                .publishedAt(n.getPublishedAt())
                .build();
    }
}
