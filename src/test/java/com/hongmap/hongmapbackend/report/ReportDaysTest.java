package com.hongmap.hongmapbackend.report;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 날짜별 운영 시간 규칙(ReportService.validateDays)과 저장 형식(ReportDaysConverter). 시각은 서버 저장 형식(UTC), 한국 = UTC+9. */
class ReportDaysTest {

    // 한국 시간 11/14 14:00 ~ 11/16 17:00
    private static final LocalDateTime START_UTC = LocalDateTime.of(2026, 11, 14, 5, 0);
    private static final LocalDateTime END_UTC = LocalDateTime.of(2026, 11, 16, 8, 0);

    private static ReportDay day(int dayOfMonth, int startHour, int endHour) {
        return new ReportDay(LocalDate.of(2026, 11, dayOfMonth), LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    @Test
    void 없으면_한번짜리() {
        assertThat(ReportService.validateDays(START_UTC, END_UTC, null)).isNull();
        assertThat(ReportService.validateDays(START_UTC, END_UTC, List.of())).isNull();
    }

    @Test
    void 날마다_시간이_달라도_첫날_시작과_마지막날_끝이_맞으면_통과() {
        List<ReportDay> days = List.of(day(14, 14, 18), day(15, 11, 15), day(16, 13, 17));
        assertThat(ReportService.validateDays(START_UTC, END_UTC, days)).isEqualTo(days);
    }

    @Test
    void 쉬는_날은_빠져도_된다() {
        assertThat(ReportService.validateDays(START_UTC, END_UTC, List.of(day(14, 14, 18), day(16, 13, 17)))).hasSize(2);
    }

    @Test
    void 하루짜리는_거절() {
        assertThatThrownBy(() -> ReportService.validateDays(START_UTC, END_UTC, List.of(day(14, 14, 18))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("한 번");
    }

    @Test
    void 자정을_넘는_시간은_거절() {
        assertThatThrownBy(() -> ReportService.validateDays(START_UTC, END_UTC, List.of(day(14, 14, 18), day(15, 20, 2), day(16, 13, 17))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("같은 날 안에서");
    }

    @Test
    void 날짜_순서가_어긋나면_거절() {
        assertThatThrownBy(() -> ReportService.validateDays(START_UTC, END_UTC, List.of(day(14, 14, 18), day(14, 15, 16), day(16, 13, 17))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("순서");
    }

    @Test
    void 기간과_어긋나면_거절() {
        assertThatThrownBy(() -> ReportService.validateDays(START_UTC, END_UTC, List.of(day(14, 13, 18), day(16, 13, 17))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("맞지 않아요");
    }

    @Test
    void 저장_형식을_오가도_같다() {
        ReportDaysConverter converter = new ReportDaysConverter();
        List<ReportDay> days = List.of(day(14, 14, 18), new ReportDay(LocalDate.of(2026, 11, 15), LocalTime.of(9, 30), LocalTime.of(12, 0)));
        String stored = converter.convertToDatabaseColumn(days);
        assertThat(stored).isEqualTo("2026-11-14 14:00-18:00,2026-11-15 09:30-12:00");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(days);
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
