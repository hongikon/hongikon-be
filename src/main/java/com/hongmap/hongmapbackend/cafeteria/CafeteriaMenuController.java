package com.hongmap.hongmapbackend.cafeteria;

import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaDayResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaWeekResponse;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** 학식 메뉴(저장해 둔 것만 읽는다 — 학교 서버를 부르지 않는다). 비로그인 허용 — SecurityConfig. */
@RestController
@RequiredArgsConstructor
public class CafeteriaMenuController {

    static final String CACHE_CONTROL = "public, max-age=600";
    static final String BAD_DATE_MESSAGE = "date 는 YYYY-MM-DD 형식이어야 해요.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    /** 터무니없는 날짜는 거절한다(조회만 하지만 범위를 좁혀 둔다). */
    private static final int MIN_YEAR = 2020;
    private static final int MAX_YEAR = 2100;

    private final CafeteriaMenuService service;

    @Tag(name = SwaggerConfig.TAG_PARTNER_ETC)
    @Operation(summary = "하루 학식 메뉴", description = "date(YYYY-MM-DD, 기본 오늘 KST)의 학생식당(제2기숙사)·교직원식당 메뉴. "
            + "메뉴가 없는 식당도 meals: [] 로 나온다. 끼니 순서 아침, 점심/점심A, 점심B, 저녁. closed=true 는 휴무(items 에 휴무 문구). "
            + "fetchedAt 은 KST. 출처 홍익대학교 홈페이지. Cache-Control: public, max-age=600.")
    @GetMapping("/cafeteria/menus")
    public ResponseEntity<CafeteriaDayResponse> day(@RequestParam(value = "date", required = false) String date) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .body(service.day(parseDate(date)));
    }

    @Tag(name = SwaggerConfig.TAG_PARTNER_ETC)
    @Operation(summary = "한 주 학식 메뉴", description = "date(기본 오늘 KST)가 속한 주의 월~금. days 항목은 하루 학식 응답과 같다.")
    @GetMapping("/cafeteria/menus/week")
    public ResponseEntity<CafeteriaWeekResponse> week(@RequestParam(value = "date", required = false) String date) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .body(service.week(parseDate(date)));
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return service.today();
        }
        try {
            LocalDate date = LocalDate.parse(raw.strip(), DATE);
            if (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_DATE_MESSAGE);
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_DATE_MESSAGE);
        }
    }
}
