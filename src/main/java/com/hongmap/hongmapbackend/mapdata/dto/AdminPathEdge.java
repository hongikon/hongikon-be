package com.hongmap.hongmapbackend.mapdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 관리자 경로망 간선. a·b 는 점 id, lengthM 은 좌표로 계산한 길이(한쪽 좌표를 못 찾으면 생략). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminPathEdge(
        Long id,
        String a,
        String b,
        Double lengthM
) {
}
