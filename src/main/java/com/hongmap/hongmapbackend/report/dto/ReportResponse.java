package com.hongmap.hongmapbackend.report.dto;

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
        String title,
        String content,
        String authorNickname,
        boolean isMine,
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
                .category(report.getCategory())
                .title(report.getTitle())
                .content(report.getContent())
                .authorNickname(report.getUser().getNickname())
                .isMine(requesterId != null && requesterId.equals(report.getUser().getId()))
                .startsAt(report.getStartsAt())
                .endsAt(report.getEndsAt())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
