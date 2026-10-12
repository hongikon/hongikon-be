package com.hongmap.hongmapbackend.mapdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 경로망의 점. id 는 프론트가 쓰던 id(예: n110) 또는 출입구 노드 id. 출입구 노드는 entrance 가 있고 좌표는
 * 그 건물의 entrances 에서 읽은 값이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapPathNode(
        String id,
        Double lat,
        Double lng,
        MapPathEntrance entrance
) {
}
