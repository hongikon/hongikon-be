package com.hongmap.hongmapbackend.cafeteria;

import org.jsoup.parser.Parser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 학교 식당 JSON(get_food_list.php 응답) → 저장할 메뉴 행.
 *
 * Content-Type 은 text/html 이지만 본문은 JSON 이다(앞에 빈 줄이 붙어 온다). result 가 "Y" 가 아니면 실패.
 * 문자열 필드는 URL 인코딩(+ = 공백)돼 있고, 메뉴에는 HTML 엔티티(&amp;amp; 등)가 남아 있다. 메뉴 줄은 \r\n 으로 나뉜다.
 * 모르는 REST_NO·PRICELEVEL, 날짜·인코딩이 깨진 행, 빈 메뉴는 건너뛰고 건수만 센다.
 */
public final class CafeteriaMenuParser {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter MENU_DATE = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\r|\\n");

    /** 이 문구가 들어간 짧은 메뉴는 휴무. */
    private static final List<String> CLOSED_KEYWORDS = List.of("운영x", "미운영", "휴무", "휴관", "휴일", "공휴일", "연휴", "휴점");
    /** 메뉴 대신 공휴일 이름만 오는 경우(학생식당은 '한글날' 한 줄만 온다). */
    private static final Set<String> HOLIDAY_NAMES = Set.of(
            "신정", "설날", "설", "삼일절", "어린이날", "부처님오신날", "석가탄신일", "현충일", "광복절",
            "추석", "개천절", "한글날", "성탄절", "크리스마스", "선거일", "임시공휴일", "대체공휴일", "노동절", "근로자의날");
    /** 휴무 문구는 한두 줄이다 — 이보다 길면 실제 메뉴로 본다. */
    private static final int CLOSED_MAX_LINES = 2;

    private CafeteriaMenuParser() {
    }

    /** 파싱된 메뉴 한 행. items 는 줄 목록(비어 있지 않음). */
    public record ParsedMenu(String restaurantCode, LocalDate menuDate, String meal, List<String> items, boolean closed) {
    }

    /** @param skippedUnknown 모르는 식당·슬롯, @param skippedInvalid 날짜·인코딩이 깨졌거나 빈 메뉴 */
    public record ParseResult(List<ParsedMenu> menus, int totalRows, int skippedUnknown, int skippedInvalid) {
    }

    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static ParseResult parse(String body) {
        if (body == null || body.isBlank()) {
            throw new ParseException("빈 응답");
        }
        JsonNode root;
        try {
            root = JSON.readTree(body.strip());
        } catch (Exception e) {
            throw new ParseException("JSON 아님", e);
        }
        if (root == null || !root.isObject()) {
            throw new ParseException("JSON 객체 아님");
        }
        String result = text(root.get("result"));
        if (!"Y".equals(result)) {
            throw new ParseException("result != Y (" + (result == null ? "없음" : result.length() > 10 ? "?" : result) + ")");
        }
        JsonNode rows = root.get("RESTDATA");
        if (rows == null || !rows.isArray()) {
            throw new ParseException("RESTDATA 없음");
        }

        List<ParsedMenu> menus = new ArrayList<>();
        int total = 0;
        int unknown = 0;
        int invalid = 0;
        for (JsonNode row : rows) {
            total++;
            Optional<CafeteriaRestaurants.Restaurant> restaurant =
                    CafeteriaRestaurants.byRestNo(String.valueOf(text(row.get("REST_NO"))).strip());
            if (restaurant.isEmpty()) {
                unknown++;
                continue;
            }
            CafeteriaRestaurants.MealSlot slot =
                    restaurant.get().slots().get(String.valueOf(text(row.get("PRICELEVEL"))).strip());
            if (slot == null) {
                unknown++;
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(String.valueOf(text(row.get("MENU_DATE"))).strip(), MENU_DATE);
                List<String> items = splitItems(decode(text(row.get("MENU"))));
                if (items.isEmpty()) {
                    invalid++;
                    continue;
                }
                menus.add(new ParsedMenu(restaurant.get().code(), date, slot.meal(), items, isClosed(items)));
            } catch (DateTimeParseException | IllegalArgumentException e) {
                invalid++;
            }
        }
        return new ParseResult(menus, total, unknown, invalid);
    }

    /** URL 디코딩(+ = 공백). 깨진 %xx 면 IllegalArgumentException. */
    static String decode(String raw) {
        if (raw == null) {
            return "";
        }
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    /** \r\n 으로 나누고 HTML 엔티티를 풀고 앞뒤 공백을 지운다. 빈 줄은 뺀다. */
    static List<String> splitItems(String menu) {
        return Arrays.stream(LINE_BREAK.split(menu))
                .map(line -> Parser.unescapeEntities(line, false).strip())
                .filter(line -> !line.isEmpty())
                .toList();
    }

    /** 휴무 문구만 온 행인지 — 한두 줄이고 '운영X'·'휴무' 같은 말이나 공휴일 이름만 있을 때. */
    static boolean isClosed(List<String> items) {
        if (items.isEmpty() || items.size() > CLOSED_MAX_LINES) {
            return false;
        }
        for (String item : items) {
            String compact = item.replace(" ", "");
            String lower = compact.toLowerCase();
            if (HOLIDAY_NAMES.contains(compact)) {
                return true;
            }
            for (String keyword : CLOSED_KEYWORDS) {
                if (lower.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isValueNode() ? node.asString() : null;
    }
}
