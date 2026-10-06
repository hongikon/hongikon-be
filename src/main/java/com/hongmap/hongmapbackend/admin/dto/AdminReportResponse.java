package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.report.Report;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 제보 검토 화면의 한 줄. 사용자용 ReportResponse 와 달리 상태·작성자·신고 수·검토 기록을 모두 싣는다.
 * 작성자는 앱에 보이는 이름(authorDisplayName)과 공개 회원 번호(authorMemberCode)로만 가리킨다 — 로그인(카카오/Apple)
 * 닉네임 원문은 실명인 경우가 많아 싣지 않는다(개인정보 보호법 제3조 최소 처리). 꼭 필요하면
 * GET /admin/users/{id}/login-name 으로 따로 열람한다.
 */
public record AdminReportResponse(
        Long id,
        String status,
        String category,
        String customCategoryLabel,
        String title,
        String content,
        /** 첫 번째 사진 보기 URL(presigned GET). 사진이 없거나 반려·삭제로 지워졌으면 null. imageUrls[0] 과 같다 */
        String imageUrl,
        /** 사진 보기 URL 들(presigned GET, 최대 3장, 등록 순서). 없거나 반려·삭제로 지워졌으면 빈 배열 */
        List<String> imageUrls,
        Long buildingId,
        String buildingName,
        Integer floor,
        BigDecimal lat,
        BigDecimal lng,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt,
        Long authorId,
        /**
         * 구버전 관리자 화면 호환용 — 예전엔 로그인 닉네임 원문이었지만 이제 authorDisplayName 과 같은 값이다.
         * 새 화면은 authorDisplayName·authorMemberCode 를 쓴다. 구버전 화면이 모두 사라지면 지운다.
         */
        String authorNickname,
        long flagCount,
        String moderationNote,
        LocalDateTime reviewedAt,
        /** 앱 사용자에게 보이는 작성자 이름(앱 닉네임, 없으면 가린 로그인 닉네임 "홍**" — DisplayNames). */
        String authorDisplayName,
        /** 작성자 공개 회원 번호(K7Q2M9XA4D). 회원 조회(q=)에 그대로 넣어 찾는다. */
        String authorMemberCode,
        /** 장소 설명(작성자가 고칠 수 있음). 없으면 null */
        String placeLabel
) {
    public static AdminReportResponse of(Report report, long flagCount, List<String> imageUrls) {
        return new AdminReportResponse(
                report.getId(),
                report.getStatus().name(),
                report.getCategory().name(),
                report.getCustomCategoryLabel(),
                report.getTitle(),
                report.getContent(),
                imageUrls.isEmpty() ? null : imageUrls.get(0),
                imageUrls,
                report.getBuilding().getId(),
                report.getBuilding().getName(),
                report.getFloor(),
                report.getLat(),
                report.getLng(),
                report.getStartsAt(),
                report.getEndsAt(),
                report.getCreatedAt(),
                report.getUser().getId(),
                report.getUser().getDisplayName(),
                flagCount,
                report.getModerationNote(),
                report.getReviewedAt(),
                report.getUser().getDisplayName(),
                report.getUser().getMemberCode(),
                report.getPlaceLabel());
    }
}
