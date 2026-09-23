package com.hongmap.hongmapbackend.news.dto;

import com.hongmap.hongmapbackend.news.News;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record NewsSummaryResponse(
        Long id,
        String title,
        /** 목록 카드용 짧은 미리보기(본문 앞부분, 최대 80자). 본문이 없으면(이미지만인 공지 등) null. */
        String preview,
        String category,
        Long departmentId,
        /** 출처 표시명(예: "컴퓨터공학과"). department가 아직 안 붙은 소식은 null — 프론트가 폴백 문구를 쓴다. */
        String departmentName,
        Long buildingId,
        LocalDateTime publishedAt
) {
    private static final int PREVIEW_MAX_LENGTH = 80;

    public static NewsSummaryResponse of(News n) {
        return NewsSummaryResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .preview(previewOf(n.getContent()))
                .category(n.getCategory())
                .departmentId(n.getDepartment() != null ? n.getDepartment().getId() : null)
                .departmentName(n.getDepartment() != null ? n.getDepartment().getName() : null)
                .buildingId(n.getBuilding() != null ? n.getBuilding().getId() : null)
                .publishedAt(n.getPublishedAt())
                .build();
    }

    private static String previewOf(String content) {
        if (content == null) return null;
        // 크롤러가 이미 태그는 제거해 주지만 줄바꿈·연속 공백은 남아 있어 한 줄로 편다.
        String flattened = content.replaceAll("\\s+", " ").trim();
        if (flattened.isEmpty()) return null;
        return flattened.length() > PREVIEW_MAX_LENGTH
                ? flattened.substring(0, PREVIEW_MAX_LENGTH) + "…"
                : flattened;
    }
}
