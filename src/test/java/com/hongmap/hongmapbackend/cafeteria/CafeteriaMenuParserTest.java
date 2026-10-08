package com.hongmap.hongmapbackend.cafeteria;

import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuParser.ParseResult;
import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuParser.ParsedMenu;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 학교 식당 JSON 파싱 — 실제 응답(2026-10-05 주, 대체공휴일·한글날 포함)을 그대로 저장한 fixture 기준. */
class CafeteriaMenuParserTest {

    static String fixture() throws IOException {
        try (InputStream in = CafeteriaMenuParserTest.class.getResourceAsStream("/cafeteria/hongik-food-list-2026-10-05.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ParsedMenu find(List<ParsedMenu> menus, String code, String date, String meal) {
        return menus.stream()
                .filter(m -> m.restaurantCode().equals(code) && m.menuDate().equals(LocalDate.parse(date)) && m.meal().equals(meal))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void parsesRealResponseWithLeadingBlankLines() throws IOException {
        // 실제 응답처럼 앞에 빈 줄·탭이 붙어 와도 읽는다.
        ParseResult result = CafeteriaMenuParser.parse("\n\n\t\t\n" + fixture());

        assertThat(result.totalRows()).isEqualTo(30);
        assertThat(result.skippedUnknown()).isZero();
        assertThat(result.skippedInvalid()).isZero();
        assertThat(result.menus()).hasSize(30);
        assertThat(result.menus()).filteredOn(m -> m.restaurantCode().equals("dorm2-student")).hasSize(20);
        assertThat(result.menus()).filteredOn(m -> m.restaurantCode().equals("mh-staff")).hasSize(10);
    }

    @Test
    void mapsSlotsPerRestaurant() throws IOException {
        List<ParsedMenu> menus = CafeteriaMenuParser.parse(fixture()).menus();

        assertThat(menus).filteredOn(m -> m.restaurantCode().equals("dorm2-student"))
                .extracting(ParsedMenu::meal).containsOnly("아침", "점심A", "점심B", "저녁");
        assertThat(menus).filteredOn(m -> m.restaurantCode().equals("mh-staff"))
                .extracting(ParsedMenu::meal).containsOnly("점심", "저녁");

        // REST_NO 3 PRICELEVEL 0 = 아침(간편식 포함), 1 = 점심A, 2 = 점심B, 3 = 저녁
        assertThat(find(menus, "dorm2-student", "2026-10-07", "아침").items()).contains("돈육김치찌개", "간편식");
        assertThat(find(menus, "dorm2-student", "2026-10-07", "점심A").items())
                .containsExactly("백미밥", "사골파국", "매콤오리훈제볶음", "두부튀김스틱&강정", "콩나물무침", "오징어젓무말랭이무침", "야채샐러드&배추치"); // 원문 오타 그대로
        assertThat(find(menus, "dorm2-student", "2026-10-07", "점심B").items()).contains("돈까스&소스", "불닭볶음면");
        assertThat(find(menus, "dorm2-student", "2026-10-07", "저녁").items()).contains("참치김치볶음밥", "생선까스&타르s");
        // REST_NO 2 PRICELEVEL 0 = 점심, 1 = 저녁
        assertThat(find(menus, "mh-staff", "2026-10-07", "점심").items().get(0)).isEqualTo("들깨미역국");
        assertThat(find(menus, "mh-staff", "2026-10-07", "저녁").items()).contains("부대찌개", "버팔로윙*봉");
    }

    @Test
    void decodesEntitiesAndTrims() throws IOException {
        List<ParsedMenu> menus = CafeteriaMenuParser.parse(fixture()).menus();
        assertThat(menus).allSatisfy(m -> assertThat(m.items()).allSatisfy(item -> {
            assertThat(item).doesNotContain("&amp;", "\r", "\n");
            assertThat(item).isEqualTo(item.strip()).isNotEmpty();
        }));
        assertThat(find(menus, "mh-staff", "2026-10-08", "저녁").items()).contains("모듬튀김(소스x)");
    }

    @Test
    void holidayRowsAreClosed() throws IOException {
        List<ParsedMenu> menus = CafeteriaMenuParser.parse(fixture()).menus();

        ParsedMenu staffHoliday = find(menus, "mh-staff", "2026-10-05", "점심");
        assertThat(staffHoliday.closed()).isTrue();
        assertThat(staffHoliday.items()).containsExactly("대체공휴일", "운영X");
        assertThat(find(menus, "dorm2-student", "2026-10-09", "저녁").closed()).isTrue();
        assertThat(find(menus, "dorm2-student", "2026-10-09", "저녁").items()).containsExactly("한글날");

        assertThat(menus).filteredOn(ParsedMenu::closed).hasSize(12)
                .allSatisfy(m -> assertThat(m.menuDate()).isIn(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-09")));
        assertThat(menus).filteredOn(m -> !m.closed()).hasSize(18);
    }

    @Test
    void closedHeuristic() {
        assertThat(CafeteriaMenuParser.isClosed(List.of("휴무"))).isTrue();
        assertThat(CafeteriaMenuParser.isClosed(List.of("추석", "연휴"))).isTrue();
        assertThat(CafeteriaMenuParser.isClosed(List.of("개천절"))).isTrue();
        assertThat(CafeteriaMenuParser.isClosed(List.of("금일 운영 x"))).isTrue();
        assertThat(CafeteriaMenuParser.isClosed(List.of("백미밥", "미역국"))).isFalse();
        assertThat(CafeteriaMenuParser.isClosed(List.of("백미밥", "미역국", "휴무"))).isFalse();
        assertThat(CafeteriaMenuParser.isClosed(List.of())).isFalse();
    }

    @Test
    void unknownRestaurantOrSlotIsSkippedAndCounted() {
        String body = """
                {"result":"Y","RESTDATA":[
                  {"REST_NO":"3","MENU_DATE":"20261007","PRICELEVEL":"1","MENU":"%EB%B0%B1%EB%AF%B8%EB%B0%A5+%EB%B0%98%EC%B0%AC%0D%0A%EA%B9%80%EC%B9%98"},
                  {"REST_NO":"9","MENU_DATE":"20261007","PRICELEVEL":"0","MENU":"x"},
                  {"REST_NO":"2","MENU_DATE":"20261007","PRICELEVEL":"7","MENU":"x"},
                  {"REST_NO":"3","MENU_DATE":"2026-10-07","PRICELEVEL":"0","MENU":"x"},
                  {"REST_NO":"3","MENU_DATE":"20261007","PRICELEVEL":"2","MENU":"%E0%A4%A"},
                  {"REST_NO":"3","MENU_DATE":"20261007","PRICELEVEL":"3","MENU":"%0D%0A+%0D%0A"}
                ]}
                """;
        ParseResult result = CafeteriaMenuParser.parse(body);

        assertThat(result.totalRows()).isEqualTo(6);
        assertThat(result.skippedUnknown()).isEqualTo(2);
        assertThat(result.skippedInvalid()).isEqualTo(3);
        assertThat(result.menus()).singleElement().satisfies(m -> {
            assertThat(m.meal()).isEqualTo("점심A");
            // + 는 공백, %0D%0A 는 줄바꿈
            assertThat(m.items()).containsExactly("백미밥 반찬", "김치");
            assertThat(m.closed()).isFalse();
        });
    }

    @Test
    void rejectsNonYesResultAndGarbage() {
        assertThatThrownBy(() -> CafeteriaMenuParser.parse("{\"result\":\"N\",\"RESTDATA\":[]}"))
                .isInstanceOf(CafeteriaMenuParser.ParseException.class).hasMessageContaining("result != Y");
        assertThatThrownBy(() -> CafeteriaMenuParser.parse("{\"RESTDATA\":[]}"))
                .isInstanceOf(CafeteriaMenuParser.ParseException.class);
        assertThatThrownBy(() -> CafeteriaMenuParser.parse("<html>점검 중</html>"))
                .isInstanceOf(CafeteriaMenuParser.ParseException.class).hasMessageContaining("JSON");
        assertThatThrownBy(() -> CafeteriaMenuParser.parse("{\"result\":\"Y\"}"))
                .isInstanceOf(CafeteriaMenuParser.ParseException.class).hasMessageContaining("RESTDATA");
        assertThatThrownBy(() -> CafeteriaMenuParser.parse("  "))
                .isInstanceOf(CafeteriaMenuParser.ParseException.class);
        assertThat(CafeteriaMenuParser.parse("{\"result\":\"Y\",\"RESTDATA\":[]}").menus()).isEmpty();
    }
}
