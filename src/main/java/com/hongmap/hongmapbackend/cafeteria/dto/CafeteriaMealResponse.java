package com.hongmap.hongmapbackend.cafeteria.dto;

import java.util.List;

/** 끼니 하나. closed 면 items 에는 휴무 문구('한글날', '운영X' 등)가 들어 있다. */
public record CafeteriaMealResponse(String meal, String time, String price, List<String> items, boolean closed) {
}
