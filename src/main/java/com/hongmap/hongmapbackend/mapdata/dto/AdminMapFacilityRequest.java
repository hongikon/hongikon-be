package com.hongmap.hongmapbackend.mapdata.dto;

import com.hongmap.hongmapbackend.mapdata.MapDataRules;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 편의시설 추가·수정 본문. map/data 의 facility 모양에서 buildingName 대신 buildingCode.
 * id(code) 는 POST 에서만 쓴다(비우면 f-xxxxxxxx 생성). lat·lng 는 둘 다 넣거나 둘 다 비운다(비우면 건물 위치).
 */
public record AdminMapFacilityRequest(
        @Pattern(regexp = MapDataRules.OPTIONAL_CODE_REGEX, message = "id 는 영문·숫자·-·_ 로 100자 이하여야 해요.") String id,
        @NotBlank(message = "종류를 골라 주세요.") String kind,
        @NotBlank(message = "건물을 골라 주세요.") String buildingCode,
        @Min(value = -10, message = "층은 -10~100 사이여야 해요.")
        @Max(value = 100, message = "층은 -10~100 사이여야 해요.") Integer floor,
        @Size(max = 255, message = "메모는 255자 이하로 입력해 주세요.") String note,
        @DecimalMin(value = "33", message = "위도는 33~39 사이여야 해요.")
        @DecimalMax(value = "39", message = "위도는 33~39 사이여야 해요.") BigDecimal lat,
        @DecimalMin(value = "124", message = "경도는 124~132 사이여야 해요.")
        @DecimalMax(value = "132", message = "경도는 124~132 사이여야 해요.") BigDecimal lng
) {
}
