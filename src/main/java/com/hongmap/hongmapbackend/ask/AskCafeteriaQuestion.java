package com.hongmap.hongmapbackend.ask;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 학식 질문 알아보기 — '오늘 학식 뭐야'를 모델이 cafeteria_menu 를 부를지에 맡기면 식단 안내(홈페이지 링크)만 보고 답했다(10-31 사용자 지적).
 * 학식 질문이면 서버가 그날 메뉴를 미리 꺼내 답 쓰기에 넘긴다. 여기서는 학식 질문인지와 어느 날짜(들)인지만 읽는다.
 */
final class AskCafeteriaQuestion {

    /** 이 낱말이 있으면 학식 질문이다. '입학식'의 '학식'은 뺀다. */
    private static final Pattern STRONG = Pattern.compile("(?<!입)학식|학생식당|교직원식당|기숙사식당|구내식당|식단|천원의\\s*아침");
    /** 끼니 낱말은 날짜 낱말과 함께 있을 때만('오늘 점심 뭐 나와'). */
    private static final Pattern MEAL = Pattern.compile("밥|점심|저녁|아침|조식|중식|석식|메뉴");
    private static final Pattern DAY = Pattern.compile("오늘|내일|모레|어제|[월화수목금토일]요일|이번\\s*주|다음\\s*주|담주|주간");
    private static final Pattern WEEK = Pattern.compile("이번\\s*주|주간|일주일");
    private static final Pattern WEEKDAY = Pattern.compile("(다음\\s*주|담주)?\\s*([월화수목금토일])요일");
    private static final Map<String, DayOfWeek> DAYS = Map.of("월", DayOfWeek.MONDAY, "화", DayOfWeek.TUESDAY, "수", DayOfWeek.WEDNESDAY,
            "목", DayOfWeek.THURSDAY, "금", DayOfWeek.FRIDAY, "토", DayOfWeek.SATURDAY, "일", DayOfWeek.SUNDAY);

    private AskCafeteriaQuestion() {
    }

    /** 학식 질문이면 메뉴를 볼 날짜(이번 주면 오늘부터 일요일까지), 아니면 빈 목록. */
    static List<LocalDate> dates(String question, LocalDate today) {
        if (question == null) {
            return List.of();
        }
        String text = question.strip();
        boolean asks = STRONG.matcher(text).find() || (MEAL.matcher(text).find() && DAY.matcher(text).find());
        if (!asks) {
            return List.of();
        }
        Matcher weekday = WEEKDAY.matcher(text);
        if (weekday.find()) {
            LocalDate day = today.with(TemporalAdjusters.nextOrSame(DAYS.get(weekday.group(2))));
            // '다음 주 금요일'(수요일에) — 이번 주 안에 걸리면 한 주 뒤로.
            return List.of(weekday.group(1) != null && !day.isAfter(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)))
                    ? day.plusWeeks(1) : day);
        }
        if (text.contains("모레")) {
            return List.of(today.plusDays(2));
        }
        if (text.contains("내일")) {
            return List.of(today.plusDays(1));
        }
        if (text.contains("어제")) {
            return List.of(today.minusDays(1));
        }
        if (WEEK.matcher(text).find()) {
            List<LocalDate> days = new ArrayList<>();
            for (LocalDate day = today; !day.isAfter(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))); day = day.plusDays(1)) {
                days.add(day);
            }
            return days;
        }
        return List.of(today);
    }
}
