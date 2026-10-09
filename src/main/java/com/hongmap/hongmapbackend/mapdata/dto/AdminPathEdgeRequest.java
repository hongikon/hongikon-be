package com.hongmap.hongmapbackend.mapdata.dto;

import jakarta.validation.constraints.NotBlank;

/** 경로망 간선 추가 — 두 점의 id. 순서는 상관없다(양방향). */
public record AdminPathEdgeRequest(
        @NotBlank(message = "이을 점(a)을 골라 주세요.") String a,
        @NotBlank(message = "이을 점(b)을 골라 주세요.") String b
) {
}
