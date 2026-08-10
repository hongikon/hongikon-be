package com.hongmap.hongmapbackend.report.dto;

import java.util.List;

public record ReportListResponse(
        List<ReportSummaryResponse> reports
) {
}
