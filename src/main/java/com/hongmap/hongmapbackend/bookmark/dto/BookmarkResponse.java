package com.hongmap.hongmapbackend.bookmark.dto;

import com.hongmap.hongmapbackend.bookmark.Bookmark;
import com.hongmap.hongmapbackend.news.dto.NewsSummaryResponse;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record BookmarkResponse(
        Long id,
        NewsSummaryResponse news,
        LocalDateTime createdAt
) {
    public static BookmarkResponse of(Bookmark b) {
        return BookmarkResponse.builder()
                .id(b.getId())
                .news(NewsSummaryResponse.of(b.getNews()))
                .createdAt(b.getCreatedAt())
                .build();
    }
}
