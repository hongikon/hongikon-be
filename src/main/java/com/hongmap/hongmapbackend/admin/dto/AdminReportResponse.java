package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.report.Report;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 관리자 제보 검토 화면의 한 줄. 사용자용 ReportResponse 와 달리 상태·작성자·신고 수·검토 기록을 모두 싣는다. */
public record AdminReportResponse(
        Long id,
        String status,
        String category,
        String customCategoryLabel,
        String title,
        String content,
        Long buildingId,
        String buildingName,
        Integer floor,
        BigDecimal lat,
        BigDecimal lng,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt,
        Long authorId,
        String authorNickname,
        long flagCount,
        String moderationNote,
        LocalDateTime reviewedAt,
        /** 앱 사용자에게 보이는 작성자 이름. authorNickname 은 검토용 로그인 닉네임 원문이다. */
        String authorDisplayName
) {
    public static AdminReportResponse of(Report report, long flagCount) {
        return new AdminReportResponse(
                report.getId(),
                report.getStatus().name(),
                report.getCategory().name(),
                report.getCustomCategoryLabel(),
                report.getTitle(),
                report.getContent(),
                report.getBuilding().getId(),
                report.getBuilding().getName(),
                report.getFloor(),
                report.getLat(),
                report.getLng(),
                report.getStartsAt(),
                report.getEndsAt(),
                report.getCreatedAt(),
                report.getUser().getId(),
                report.getUser().getNickname(),
                flagCount,
                report.getModerationNote(),
                report.getReviewedAt(),
                report.getUser().getDisplayName());
    }
}
