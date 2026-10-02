package com.hongmap.hongmapbackend.report.dto;

import com.hongmap.hongmapbackend.report.Report;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record ReportCreateRequest(
        @NotNull
        Long buildingId,

        @NotNull
        Integer floor,

        @NotNull
        java.math.BigDecimal lat,

        @NotNull
        java.math.BigDecimal lng,

        @NotBlank
        String category,

        @Size(max = 50)
        String customCategoryLabel,

        @NotBlank
        @Size(max = 100)
        String title,

        @Size(max = 500)
        String content,

        /** POST /reports/images 로 받은 key 들(최대 3장, 순서대로 표시, 중복 불가). 사진이 없으면 생략. */
        @Size(max = Report.MAX_IMAGES)
        List<@Size(max = 200) String> imageKeys,

        /**
         * 구버전 앱(사진 1장)용. imageKeys 가 비어 있을 때만 쓴다 — 새 앱은 구버전 서버 대비로
         * imageKey(첫 장)와 imageKeys 를 함께 보낼 수 있고, 이때는 imageKeys 가 우선한다.
         */
        @Size(max = 200)
        String imageKey,

        /** 시작 시각(UTC). 지금(10분 여유)부터 report.startsAt.maxDays(기본 14)일 이내 — 미리 올리는 예정 제보도 된다. */
        @NotNull
        LocalDateTime startsAt,

        /** 종료 시각(UTC). startsAt 보다 뒤, 진행 시간은 report.maxDurationHours(기본 12)시간 이내. */
        @NotNull
        @Future(message = "종료 시각이 이미 지났어요. 시간을 다시 골라 주세요.")
        LocalDateTime endsAt
) {
    /** 붙일 사진 키(순서대로). imageKeys 가 있으면 그것, 없으면 imageKey 1장, 둘 다 없으면 빈 목록. */
    public List<String> requestedImageKeys() {
        if (imageKeys != null && !imageKeys.isEmpty()) {
            return imageKeys;
        }
        if (imageKey != null && !imageKey.isBlank()) {
            return List.of(imageKey);
        }
        return List.of();
    }
}
