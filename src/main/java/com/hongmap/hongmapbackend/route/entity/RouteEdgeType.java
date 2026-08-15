package com.hongmap.hongmapbackend.route.entity;

import java.util.Arrays;

// RouteEdge.edgeType에 허용되는 값 목록. 컬럼 자체는 자유 varchar를 유지하되(향후 확장 대비),
// 엔티티 생성 시점에 이 목록으로 값을 검증해 오타/미정의 타입이 DB에 들어가는 것을 막는다.
public enum RouteEdgeType {
    CORRIDOR,
    ELEVATOR,
    STAIRS,
    RAMP;

    public static boolean isValid(String value) {
        return Arrays.stream(values()).anyMatch(type -> type.name().equals(value));
    }
}
