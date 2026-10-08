package com.hongmap.hongmapbackend.cafeteria.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 하루치 학식. fetchedAt 은 그날 메뉴를 마지막으로 가져온 시각(KST, 초 단위) — 메뉴가 없으면 null. */
public record CafeteriaDayResponse(LocalDate date, String source, String sourceUrl, LocalDateTime fetchedAt,
                                   List<CafeteriaRestaurantResponse> restaurants) {
}
