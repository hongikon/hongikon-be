package com.hongmap.hongmapbackend.report.dto;

import com.hongmap.hongmapbackend.report.AuthorKeys;
import com.hongmap.hongmapbackend.report.Report;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        /** 첫 번째 사진 보기 URL(presigned GET, 1시간 유효). 사진이 없으면 null. 구버전 앱 호환용 — imageUrls[0] 과 같다 */
        String imageUrl,
        /** 사진 보기 URL 들(presigned GET, 1시간 유효, 최대 3장, 등록 순서). 사진이 없으면 빈 배열 */
        List<String> imageUrls,
        boolean isMine,
        /** 작성자 식별값(불투명, 사용자 id 가 아님). 앱의 "이 사용자의 제보 숨기기"용. AuthorKeys 참고 */
        String authorKey,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt,
        /** 작성자 표시 이름(앱 닉네임 또는 가린 로그인 닉네임). authorNickname 도 같은 값이며 구버전 앱 호환용으로 남겨 둔다. */
        String authorDisplayName
) {
    public static ReportSummaryResponse of(Report report, Long requesterId, List<String> imageUrls) {
        return ReportSummaryResponse.builder()
                .id(report.getId())
                .buildingId(report.getBuilding() != null ? report.getBuilding().getId() : null)
                .floor(report.getFloor())
                .lat(report.getLat())
                .lng(report.getLng())
                .category(report.getCategory().name())
                .customCategoryLabel(report.getCustomCategoryLabel())
                .title(report.getTitle())
 .authorNickname(report.getUser().getDisplayName())
                .imageUrl(imageUrls.isEmpty() ? null : imageUrls.get(0))
                .imageUrls(imageUrls)
                .isMine(requesterId != null && requesterId.equals(report.getUser().getId()))
                .authorKey(AuthorKeys.of(report.getUser().getId()))
                .startsAt(report.getStartsAt())
                .endsAt(report.getEndsAt())
                .createdAt(report.getCreatedAt())
                .authorDisplayName(report.getUser().getDisplayName())
                .build();
    }
}
