package com.hongmap.hongmapbackend.report.dto;

import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 제보 내역 한 줄. 작성자 본인에게만 내려가므로 작성자 이름·신고자 정보는 넣지 않는다.
 *
 * @param status         저장된 검토 상태(PENDING/ACTIVE/REJECTED/HIDDEN/DELETED)
 * @param displayStatus  화면용 상태. status 에 시간을 더해 PENDING/SCHEDULED/ACTIVE/ENDED/REJECTED/HIDDEN/DELETED 중 하나
 * @param moderationNote 작성자에게 보여 줄 사유. REJECTED·HIDDEN 일 때만, 그 외에는 null
 */
@Builder
public record MyReportResponse(
        Long id,
        String title,
        String category,
        String customCategoryLabel,
        Long buildingId,
        String buildingName,
        Integer floor,
        BigDecimal lat,
        BigDecimal lng,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String status,
        String displayStatus,
        String moderationNote,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        /** 첫 번째 사진 보기 URL. 없으면 null — imageUrls[0] 과 같다 */
        String imageUrl,
        /** 사진 보기 URL 들(presigned GET, 최대 3장). 반려·삭제된 제보는 사진을 지우므로 빈 배열 */
        List<String> imageUrls
) {

    public static MyReportResponse of(Report report, LocalDateTime now, List<String> imageUrls) {
        ReportStatus status = report.getStatus();
        boolean showNote = status == ReportStatus.REJECTED || status == ReportStatus.HIDDEN;
        return MyReportResponse.builder()
                .id(report.getId())
                .title(report.getTitle())
                .category(report.getCategory().name())
                .customCategoryLabel(report.getCustomCategoryLabel())
                .buildingId(report.getBuilding().getId())
                .buildingName(report.getBuilding().getName())
                .floor(report.getFloor())
                .lat(report.getLat())
                .lng(report.getLng())
                .startsAt(report.getStartsAt())
                .endsAt(report.getEndsAt())
                .status(status.name())
                .displayStatus(displayStatus(report, now))
                .moderationNote(showNote ? report.getModerationNote() : null)
                .reviewedAt(report.getReviewedAt())
                .createdAt(report.getCreatedAt())
                .imageUrl(imageUrls.isEmpty() ? null : imageUrls.get(0))
                .imageUrls(imageUrls)
                .build();
    }

    /**
     * 승인 대기·승인된 제보는 기간이 지나면 ENDED(지도에서 내려감 — 승인 대기 중 끝난 것도 더는 올라가지 않는다).
     * 승인됐지만 아직 시작 전이면 SCHEDULED. 반려·숨김·삭제는 그대로.
     */
    static String displayStatus(Report report, LocalDateTime now) {
        ReportStatus status = report.getStatus();
        if (status != ReportStatus.PENDING && status != ReportStatus.ACTIVE) {
            return status.name();
        }
        if (report.getEndsAt() != null && report.getEndsAt().isBefore(now)) {
            return "ENDED";
        }
        if (status == ReportStatus.ACTIVE && report.getStartsAt() != null && report.getStartsAt().isAfter(now)) {
            return "SCHEDULED";
        }
        return status.name();
    }
}
