package com.hongmap.hongmapbackend.report;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 여러 날 제보의 하루 운영 시간(한국 시간, 11-08). 날마다 시간이 다를 수 있고, 쉬는 날은 목록에 없다.
 * JSON 은 {"date":"2026-11-14","start":"14:00","end":"18:00"}.
 */
public record ReportDay(LocalDate date, LocalTime start, LocalTime end) {
}
