package com.hongmap.hongmapbackend.mapdata.dto;

/** 편의시설 편집에서 건물을 고르는 목록 항목. name 은 표시 이름. */
public record AdminMapBuilding(Long id, String code, String name) {
}
