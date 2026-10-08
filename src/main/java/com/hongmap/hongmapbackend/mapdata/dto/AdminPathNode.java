package com.hongmap.hongmapbackend.mapdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 관리자 경로망 점. lat·lng 는 출입구 노드면 건물 entrances 에서 읽은 값(못 찾으면 생략, broken=true).
 * degree = 이어진 간선 수.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminPathNode(
        String id,
        String kind,
        Double lat,
        Double lng,
        String buildingCode,
        String entranceLabel,
        int degree,
        boolean broken
) {
}
