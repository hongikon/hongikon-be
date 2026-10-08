package com.hongmap.hongmapbackend.cafeteria;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 메뉴를 가져오는 식당과 끼니 슬롯 대응표(정적 설정).
 *
 * 학교 JSON 의 REST_NO(식당)·PRICELEVEL(끼니 슬롯)은 이름이 없는 번호라 여기서 라벨·시간·가격으로 바꾼다.
 * 대응은 같은 날 apps.hongik.ac.kr/food/food_m.php 화면과 대조해 정했다(2026-10-07). 시간·가격은 홈페이지 공식 값만 쓴다.
 * 학기가 바뀌면 food_m.php 와 다시 대조한다. 모르는 REST_NO·슬롯은 건너뛰고 경고 로그(건수)만 남긴다.
 */
public final class CafeteriaRestaurants {

    private CafeteriaRestaurants() {
    }

    /** 끼니 하나 — 라벨, 운영 시간, 가격(표시용 문구). */
    public record MealSlot(String meal, String time, String price) {
    }

    /**
     * @param restNo     학교 JSON 의 REST_NO
     * @param facilityId 지도 편의시설 id(campus_facilities.code)
     * @param slots      PRICELEVEL → 끼니
     */
    public record Restaurant(String code, String restNo, String facilityId, String name, Map<String, MealSlot> slots) {
    }

    public static final String DORM2_STUDENT = "dorm2-student";
    public static final String MH_STAFF = "mh-staff";

    /** 응답 순서 그대로. */
    public static final List<Restaurant> ALL = List.of(
            new Restaurant(DORM2_STUDENT, "3", "hi-dorm2-b2f-restaurant-01", "학생식당", Map.of(
                    "0", new MealSlot("아침", "08:00~09:00", "1,000원 (천원의 아침밥 · 일반 7,500원)"),
                    "1", new MealSlot("점심A", "11:30~14:00", "5,800원 (일반 7,500원)"),
                    "2", new MealSlot("점심B", "11:30~14:00", "5,800원 (일반 7,500원)"),
                    "3", new MealSlot("저녁", "17:30~18:50", "5,800원 (일반 7,500원)"))),
            new Restaurant(MH_STAFF, "2", "hi-mh-16f-restaurant", "교직원식당", Map.of(
                    "0", new MealSlot("점심", "11:30~14:00", "9,000원"),
                    "1", new MealSlot("저녁", "17:00~18:30", "9,000원"))));

    /** 끼니 표시 순서: 아침, 점심/점심A, 점심B, 저녁. */
    private static final List<String> MEAL_ORDER = List.of("아침", "점심", "점심A", "점심B", "저녁");

    public static Optional<Restaurant> byRestNo(String restNo) {
        return ALL.stream().filter(r -> r.restNo().equals(restNo)).findFirst();
    }

    public static Optional<Restaurant> byCode(String code) {
        return ALL.stream().filter(r -> r.code().equals(code)).findFirst();
    }

    /** 표시 순서 값(작을수록 먼저). 모르는 라벨은 맨 뒤. */
    public static int mealRank(String meal) {
        int index = MEAL_ORDER.indexOf(meal);
        return index < 0 ? MEAL_ORDER.size() : index;
    }
}
