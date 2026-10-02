package com.hongmap.hongmapbackend.report.dto;

import com.hongmap.hongmapbackend.report.Report;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        /** 첫 번째 사진 보기 URL(presigned GET, 1시간 유효). 사진이 없으면 null. 구버전 앱 호환용 — imageUrls[0] 과 같다 */
        String imageUrl,
        /** 사진 보기 URL 들(presigned GET, 1시간 유효, 최대 3장, 등록 순서). 사진이 없으면 빈 배열 */
        List<String> imageUrls,
        boolean isMine,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String status,
        LocalDateTime createdAt,
        /** 작성자 표시 이름(앱 닉네임 또는 가린 로그인 닉네임). authorNickname 도 같은 값이며 구버전 앱 호환용으로 남겨 둔다. */
        String authorDisplayName
) {
    public static ReportResponse of(Report report, Long requesterId, List<String> imageUrls) {
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
                .authorNickname(report.getUser().getDisplayName())
                .imageUrl(imageUrls.isEmpty() ? null : imageUrls.get(0))
                .imageUrls(imageUrls)
                .isMine(requesterId != null && requesterId.equals(report.getUser().getId()))
                .startsAt(report.getStartsAt())
                .endsAt(report.getEndsAt())
                .status(report.getStatus().name())
                .createdAt(report.getCreatedAt())
                .authorDisplayName(report.getUser().getDisplayName())
                .build();
    }
}
