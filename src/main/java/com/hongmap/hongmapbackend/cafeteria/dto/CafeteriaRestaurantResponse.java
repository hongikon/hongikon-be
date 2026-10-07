package com.hongmap.hongmapbackend.cafeteria.dto;

import java.util.List;

/** 식당 하나. 그날 메뉴가 없으면 meals 는 빈 배열. facilityId 는 지도 편의시설 id. */
public record CafeteriaRestaurantResponse(String code, String facilityId, String name, List<CafeteriaMealResponse> meals) {
}
