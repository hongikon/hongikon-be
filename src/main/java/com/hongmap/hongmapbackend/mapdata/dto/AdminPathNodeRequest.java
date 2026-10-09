package com.hongmap.hongmapbackend.mapdata.dto;

import com.hongmap.hongmapbackend.mapdata.MapDataRules;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 경로망 점 추가·수정 본문.
 * - WAYPOINT: lat·lng 필수, buildingCode·entranceLabel 은 비운다. id 를 비우면 pn-xxxxxxxx 를 만든다.
 * - ENTRANCE: buildingCode·entranceLabel 필수, lat·lng 는 비운다(좌표는 건물 entrances 에서 읽는다).
 *   id 는 e-{건물 code}-{라벨} 로 정해진다 — 비우거나 같은 값만 넣을 수 있다.
 * 수정(PUT)은 중간점 좌표만 바꿀 수 있다.
 */
public record AdminPathNodeRequest(
        @Pattern(regexp = MapDataRules.OPTIONAL_CODE_REGEX, message = "id 는 영문·숫자·-·_ 로 100자 이하여야 해요.") String id,
        @NotBlank(message = "종류(WAYPOINT/ENTRANCE)를 골라 주세요.") String kind,
        @DecimalMin(value = "33", message = "위도는 33~39 사이여야 해요.")
        @DecimalMax(value = "39", message = "위도는 33~39 사이여야 해요.") BigDecimal lat,
        @DecimalMin(value = "124", message = "경도는 124~132 사이여야 해요.")
        @DecimalMax(value = "132", message = "경도는 124~132 사이여야 해요.") BigDecimal lng,
        @Size(max = 100, message = "건물 code 는 100자 이하여야 해요.") String buildingCode,
        @Size(max = 50, message = "출입구 라벨은 50자 이하여야 해요.") String entranceLabel
) {
}
