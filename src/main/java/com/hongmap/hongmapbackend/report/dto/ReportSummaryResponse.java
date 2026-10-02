package com.hongmap.hongmapbackend.report.dto;

import com.hongmap.hongmapbackend.report.AuthorKeys;
import com.hongmap.hongmapbackend.report.Report;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 목록 조회용 — content(본문) 제외한 요약본.
 */
@Builder
public record ReportSummaryResponse(
        Long id,
        Long buildingId,
        Integer floor,
        BigDecimal lat,
        BigDecimal lng,
        String category,
        String customCategoryLabel,
        String title,
        String authorNickname,
        boolean isMine,
        /** 작성자 식별값(불투명, 사용자 id 가 아님). 앱의 "이 사용자의 제보 숨기기"용. AuthorKeys 참고 */
        String authorKey,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt
) {
    public static ReportSummaryResponse of(Report report, Long requesterId) {
        return ReportSummaryResponse.builder()
                .id(report.getId())
                .buildingId(report.getBuilding() != null ? report.getBuilding().getId() : null)
                .floor(report.getFloor())
                .lat(report.getLat())
                .lng(report.getLng())
                .category(report.getCategory().name())
                .customCategoryLabel(report.getCustomCategoryLabel())
                .title(report.getTitle())
                .authorNickname(report.getUser().getNickname())
                .isMine(requesterId != null && requesterId.equals(report.getUser().getId()))
                .authorKey(AuthorKeys.of(report.getUser().getId()))
                .startsAt(report.getStartsAt())
                .endsAt(report.getEndsAt())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
