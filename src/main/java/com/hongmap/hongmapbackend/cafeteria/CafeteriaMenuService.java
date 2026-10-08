package com.hongmap.hongmapbackend.cafeteria;

import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuParser.ParsedMenu;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaDayResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaMealResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaRestaurantResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaWeekResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 학식 메뉴 저장(upsert)과 조회. */
@Service
@RequiredArgsConstructor
public class CafeteriaMenuService {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final String SOURCE = "홍익대학교 홈페이지";
    public static final String SOURCE_URL = "https://www.hongik.ac.kr/kr/life/seoul-cafeteria.do";

    private final CafeteriaMenuRepository repository;
    private final Clock clock;

    /** @param inserted 새로 넣은 행, @param updated 내용을 덮어쓴 행 */
    public record UpsertResult(int inserted, int updated) {
    }

    /** 파싱한 메뉴를 (식당, 날짜, 끼니) 기준으로 넣거나 덮어쓴다. */
    @Transactional
    public UpsertResult upsert(List<ParsedMenu> menus) {
        if (menus.isEmpty()) {
            return new UpsertResult(0, 0);
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        Set<LocalDate> dates = menus.stream().map(ParsedMenu::menuDate).collect(Collectors.toSet());
        Map<String, CafeteriaMenu> existing = new HashMap<>();
        for (CafeteriaMenu row : repository.findByMenuDateIn(dates)) {
            existing.put(key(row.getRestaurantCode(), row.getMenuDate(), row.getMeal()), row);
        }
        int inserted = 0;
        List<CafeteriaMenu> updatedRows = new ArrayList<>();
        for (ParsedMenu menu : menus) {
            String items = String.join("\n", menu.items());
            String key = key(menu.restaurantCode(), menu.menuDate(), menu.meal());
            CafeteriaMenu row = existing.get(key);
            if (row == null) {
                row = repository.save(new CafeteriaMenu(menu.restaurantCode(), menu.menuDate(), menu.meal(),
                        items, menu.closed(), now));
                existing.put(key, row);
                inserted++;
            } else {
                row.replace(items, menu.closed(), now);
                updatedRows.add(row);
            }
        }
        // 트랜잭션 밖에서 불려도(테스트) 반영되게 명시적으로 저장한다.
        repository.saveAll(updatedRows);
        return new UpsertResult(inserted, updatedRows.size());
    }

    /** 오늘(KST). */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    /** 이번 주(월~금)에 저장된 메뉴가 하나라도 있는지. */
    @Transactional(readOnly = true)
    public boolean hasWeek(LocalDate date) {
        LocalDate monday = monday(date);
        return repository.existsByMenuDateBetween(monday, monday.plusDays(4));
    }

    @Transactional(readOnly = true)
    public CafeteriaDayResponse day(LocalDate date) {
        return toDay(date, repository.findByMenuDateBetween(date, date));
    }

    /** date 가 속한 주의 월~금. */
    @Transactional(readOnly = true)
    public CafeteriaWeekResponse week(LocalDate date) {
        LocalDate monday = monday(date);
        Map<LocalDate, List<CafeteriaMenu>> byDate = repository.findByMenuDateBetween(monday, monday.plusDays(4)).stream()
                .collect(Collectors.groupingBy(CafeteriaMenu::getMenuDate));
        List<CafeteriaDayResponse> days = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate d = monday.plusDays(i);
            days.add(toDay(d, byDate.getOrDefault(d, List.of())));
        }
        return new CafeteriaWeekResponse(days);
    }

    static LocalDate monday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private CafeteriaDayResponse toDay(LocalDate date, List<CafeteriaMenu> rows) {
        Map<String, List<CafeteriaMenu>> byRestaurant = rows.stream()
                .collect(Collectors.groupingBy(CafeteriaMenu::getRestaurantCode));
        List<CafeteriaRestaurantResponse> restaurants = new ArrayList<>();
        for (CafeteriaRestaurants.Restaurant restaurant : CafeteriaRestaurants.ALL) {
            List<CafeteriaMealResponse> meals = byRestaurant.getOrDefault(restaurant.code(), List.of()).stream()
                    .sorted(Comparator.comparingInt((CafeteriaMenu m) -> CafeteriaRestaurants.mealRank(m.getMeal()))
                            .thenComparing(CafeteriaMenu::getMeal))
                    .map(m -> toMeal(restaurant, m))
                    .toList();
            restaurants.add(new CafeteriaRestaurantResponse(restaurant.code(), restaurant.facilityId(), restaurant.name(), meals));
        }
        LocalDateTime fetchedAt = rows.stream()
                .map(CafeteriaMenu::getFetchedAt)
                .max(Comparator.naturalOrder())
                .map(utc -> utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS))
                .orElse(null);
        return new CafeteriaDayResponse(date, SOURCE, SOURCE_URL, fetchedAt, restaurants);
    }

    private static CafeteriaMealResponse toMeal(CafeteriaRestaurants.Restaurant restaurant, CafeteriaMenu menu) {
        CafeteriaRestaurants.MealSlot slot = restaurant.slots().values().stream()
                .filter(s -> s.meal().equals(menu.getMeal()))
                .findFirst()
                .orElse(null);
        List<String> items = Arrays.stream(menu.getItems().split("\n"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        return new CafeteriaMealResponse(menu.getMeal(), slot == null ? null : slot.time(), slot == null ? null : slot.price(),
                items, menu.isClosed());
    }

    private static String key(String restaurantCode, LocalDate date, String meal) {
        return restaurantCode + "|" + date + "|" + meal;
    }
}
