package com.hongmap.hongmapbackend.cafeteria.dto;

import java.util.List;

/** 한 주(월~금) 학식. */
public record CafeteriaWeekResponse(List<CafeteriaDayResponse> days) {
}
