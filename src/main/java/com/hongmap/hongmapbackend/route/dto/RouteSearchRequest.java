package com.hongmap.hongmapbackend.route.dto;

import jakarta.validation.constraints.NotBlank;

public record RouteSearchRequest(
        @NotBlank String startBuildingCode,
        @NotBlank String endBuildingCode,
        Boolean useElevator,
        Boolean simplified
) {
    // 명시적으로 넘어오지 않으면(null) 엘리베이터 사용을 기본 허용한다.
    public boolean useElevatorOrDefault() {
        return useElevator == null || useElevator;
    }

    // 명시적으로 넘어오지 않으면(null) 간략화 응답은 기본 비활성.
    public boolean simplifiedOrDefault() {
        return simplified != null && simplified;
    }
}
