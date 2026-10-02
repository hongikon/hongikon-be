package com.hongmap.hongmapbackend.report.dto;

import com.hongmap.hongmapbackend.report.AuthorKeys;
import com.hongmap.hongmapbackend.report.Report;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상세(생성 응답)용 — content 포함.
 */
@Builder
public record ReportResponse(
        Long id,
        Long buildingId,
        Integer floor,
        BigDecimal lat,
        BigDecimal lng,
        String category,
        String customCategoryLabel,
        String title,
        String content,
        String authorNickname,
        boolean isMine,
        /** 작성자 식별값(불투명, 사용자 id 가 아님). 앱의 "이 사용자의 제보 숨기기"용. AuthorKeys 참고 */
        String authorKey,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String status,
        LocalDateTime createdAt
) {
    public static ReportResponse of(Report report, Long requesterId) {
        return ReportResponse.builder()
                .id(report.getId())
                .buildingId(report.getBuilding() != null ? report.getBuilding().getId() : null)
                .floor(report.getFloor())
                .lat(report.getLat())
                .lng(report.getLng())
                .category(report.getCategory().name())
                .customCategoryLabel(report.getCustomCategoryLabel())
                .title(report.getTitle())
                .content(report.getContent())
                .authorNickname(report.getUser().getNickname())
                .isMine(requesterId != null && requesterId.equals(report.getUser().getId()))
                .authorKey(AuthorKeys.of(report.getUser().getId()))
                .startsAt(report.getStartsAt())
                .endsAt(report.getEndsAt())
                .status(report.getStatus().name())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
