package com.hongmap.hongmapbackend.report.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

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

        /** POST /reports/images 로 받은 key. 사진이 없으면 생략. */
        @Size(max = 200)
        String imageKey,

        @NotNull
        LocalDateTime startsAt,

        @NotNull
        @Future
        LocalDateTime endsAt
) {
}
