package com.hongmap.hongmapbackend.report.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** PUT /reports/{id}/schedule — 공식 계정 작성자의 제보 시각 수정. 시각은 등록(ReportCreateRequest)과 같은 UTC. */
public record ReportScheduleRequest(
        @NotNull
        LocalDateTime startsAt,

        @NotNull
        @Future(message = "종료 시각이 이미 지났어요. 시간을 다시 골라 주세요.")
        LocalDateTime endsAt
) {
}
