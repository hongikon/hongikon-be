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
@Builder(toBuilder = true)
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
        String authorDisplayName,
        /** 공개 댓글 수(GET /reports 목록에서만 채움 — ReportCommentCounts, 쿼리 1번). 그 밖의 응답에서는 null */
        Long commentCount,
        /** 🔥 수(GET /reports 목록에서만 채움 — ReportCommunityStats). 그 밖의 응답에서는 null */
        Long fireCount,
        /** 최근 report.hot.window-minutes(기본 60분) 안의 🔥 수 */
        Long recentFireCount,
        /** recentFireCount 가 report.hot.threshold(기본 5) 이상이면 true */
        Boolean hot,
        /** 요청한 사람이 🔥를 눌렀는지(게스트는 false) */
        Boolean firedByMe,
        /** 요청한 사람이 관심 제보로 등록했는지(게스트는 false) */
        Boolean followedByMe,
        /** 조회 수(사람·기기마다 하루 한 번, 익명 집계) */
        Long viewCount,
        /** 작성자 본인에게만: "이 제보 알림" 켜짐 여부. 남의 제보·게스트에게는 null */
        Boolean notifyEnabled
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

    /** 같은 내용에 커뮤니티 값(🔥·관심·조회·작성자 알림)을 채운 사본. */
    public ReportSummaryResponse withCommunity(long fireCount, long recentFireCount, boolean hot, boolean firedByMe,
                                               boolean followedByMe, long viewCount, Boolean notifyEnabled) {
        return toBuilder().fireCount(fireCount).recentFireCount(recentFireCount).hot(hot).firedByMe(firedByMe)
                .followedByMe(followedByMe).viewCount(viewCount).notifyEnabled(notifyEnabled).build();
    }

    /** 같은 내용에 댓글 수만 바꾼 사본. */
    public ReportSummaryResponse withCommentCount(long count) {
        return toBuilder().commentCount(count).build();
    }
}
