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
        /**
         * 수집 게시판 id(프론트 TREE_DATA 리프 id와 동일). 학과 게시판이면 학과명, 대학공지면 "학사"/"장학" 등.
         * 대학공지는 departmentName이 null이라 프론트 구독 필터링은 이 값을 우선 쓴다. 컬럼 도입 전 글은 null일 수 있다.
         */
        String sourceId,
        Long buildingId,
        LocalDateTime publishedAt,
        /** 게시판 상단 고정 공지인지. 고정을 구분하는 게시판(국제협력본부)만 true가 될 수 있다. */
        boolean pinned
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
                .sourceId(n.getSourceId())
                .buildingId(n.getBuilding() != null ? n.getBuilding().getId() : null)
                .publishedAt(n.getPublishedAt())
                .pinned(n.isPinned())
                .build();
    }
}
