package com.hongmap.hongmapbackend.report.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportFlagRequest(
        @NotBlank
        String reason
) {
}
