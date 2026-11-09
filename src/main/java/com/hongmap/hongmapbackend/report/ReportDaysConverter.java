package com.hongmap.hongmapbackend.report;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * reports.schedule_days ↔ 날짜별 운영 시간. 최대 8일이라 따로 테이블을 두지 않고 한 칸에 적는다:
 * "2026-11-14 14:00-18:00,2026-11-15 13:00-17:00". 비어 있으면 NULL(한 번짜리 제보).
 */
@Converter
public class ReportDaysConverter implements AttributeConverter<List<ReportDay>, String> {

    @Override
    public String convertToDatabaseColumn(List<ReportDay> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (ReportDay day : days) {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append(day.date()).append(' ').append(day.start()).append('-').append(day.end());
        }
        return out.toString();
    }

    @Override
    public List<ReportDay> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<ReportDay> days = new ArrayList<>();
        for (String part : value.split(",")) {
            String[] dateAndTimes = part.trim().split(" ");
            String[] times = dateAndTimes[1].split("-");
            days.add(new ReportDay(LocalDate.parse(dateAndTimes[0]), LocalTime.parse(times[0]), LocalTime.parse(times[1])));
        }
        return List.copyOf(days);
    }
}
