package com.hongmap.hongmapbackend.mapdata.dto;

/** 출입구 노드가 가리키는 출입구 — building 은 buildings[].code, label 은 그 건물 entrances[].label. */
public record MapPathEntrance(String building, String label) {
}
