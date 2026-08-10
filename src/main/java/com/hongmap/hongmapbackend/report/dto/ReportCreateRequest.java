package com.hongmap.hongmapbackend.report.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ReportCreateRequest(
        Long buildingId,

        Integer floor,

        @NotNull
        java.math.BigDecimal lat,

        @NotNull
        java.math.BigDecimal lng,

        @NotBlank
        String category,

        @NotBlank
        @Size(max = 100)
        String title,

        @Size(max = 500)
        String content,

        @NotNull
        LocalDateTime startsAt,

        @NotNull
        @Future
        LocalDateTime endsAt
) {
}
