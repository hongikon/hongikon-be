package com.hongmap.hongmapbackend.report.dto;

import com.hongmap.hongmapbackend.report.ReportDay;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** PUT /reports/{id}/schedule — 공식 계정 작성자의 제보 시각 수정. 시각은 등록(ReportCreateRequest)과 같은 UTC. */
public record ReportScheduleRequest(
        @NotNull
        LocalDateTime startsAt,

        @NotNull
        @Future(message = "종료 시각이 이미 지났어요. 시간을 다시 골라 주세요.")
        LocalDateTime endsAt,

        /**
         * 여러 날 제보의 날짜별 운영 시간(한국 시간, 날짜 순, 쉬는 날은 빼고, 최대 8일). 한 번짜리면 생략. 시간 수정에서 생략하면 한 번짜리로 바뀐다.
         * 보내면 startsAt 은 첫날 start, endsAt 은 마지막 날 end 여야 한다(ReportService.validateDays).
         */
        @Size(max = 8, message = "운영 날짜는 8일까지 정할 수 있어요.")
        List<ReportDay> days
) {
}
