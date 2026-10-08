package com.hongmap.hongmapbackend.cafeteria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 식당·날짜·끼니 하나의 학식 메뉴. 홍익대 홈페이지에서 가져온 공개 메뉴 텍스트뿐이다(개인정보 없음).
 * items 는 메뉴 줄을 '\n' 으로 이은 것, closed 는 메뉴 대신 휴무 문구('한글날', '운영X' 등)만 온 날.
 * fetched_at 은 UTC(서버 기준) — API 가 KST 로 바꿔 내려준다.
 *
 * DB: cafeteria_menus (db/create_cafeteria_menus_table.sql).
 */
@Entity
@Table(name = "cafeteria_menus",
        uniqueConstraints = @UniqueConstraint(name = "uk_cafeteria_menus_restaurant_date_meal",
                columnNames = {"restaurant_code", "menu_date", "meal"}),
        indexes = @Index(name = "idx_cafeteria_menus_menu_date", columnList = "menu_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CafeteriaMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_code", length = 50, nullable = false)
    private String restaurantCode;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Column(name = "meal", length = 20, nullable = false)
    private String meal;

    @Column(name = "items", columnDefinition = "TEXT", nullable = false)
    private String items;

    @Column(name = "closed", nullable = false)
    private boolean closed;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    public CafeteriaMenu(String restaurantCode, LocalDate menuDate, String meal, String items, boolean closed,
                         LocalDateTime fetchedAt) {
        this.restaurantCode = restaurantCode;
        this.menuDate = menuDate;
        this.meal = meal;
        this.items = items;
        this.closed = closed;
        this.fetchedAt = fetchedAt;
    }

    /** 다시 가져온 내용으로 덮어쓴다. */
    void replace(String items, boolean closed, LocalDateTime fetchedAt) {
        this.items = items;
        this.closed = closed;
        this.fetchedAt = fetchedAt;
    }
}
