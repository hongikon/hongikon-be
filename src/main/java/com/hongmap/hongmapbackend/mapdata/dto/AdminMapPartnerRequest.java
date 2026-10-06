package com.hongmap.hongmapbackend.mapdata.dto;

import com.hongmap.hongmapbackend.mapdata.MapDataRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 제휴업체 추가·수정 본문. map/data 의 partner 모양과 같다.
 * id(code) 는 POST 에서만 쓴다(비우면 서버가 p-xxxxxxxx 생성). PUT 은 경로의 code 를 쓰고 본문 id 는 무시한다.
 * category·affiliations·mapIcon 의 허용 값은 MapDataRules — 서비스에서 확인한다.
 */
public record AdminMapPartnerRequest(
        @Pattern(regexp = MapDataRules.OPTIONAL_CODE_REGEX, message = "id 는 영문·숫자·-·_ 로 100자 이하여야 해요.") String id,
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 100, message = "이름은 100자 이하로 입력해 주세요.") String name,
        @NotBlank(message = "분류를 골라 주세요.") String category,
        @Size(max = 20, message = "소속이 너무 많아요.") List<String> affiliations,
        String mapIcon,
        @NotNull(message = "위도를 입력해 주세요.")
        @DecimalMin(value = "33", message = "위도는 33~39 사이여야 해요.")
        @DecimalMax(value = "39", message = "위도는 33~39 사이여야 해요.") BigDecimal lat,
        @NotNull(message = "경도를 입력해 주세요.")
        @DecimalMin(value = "124", message = "경도는 124~132 사이여야 해요.")
        @DecimalMax(value = "132", message = "경도는 124~132 사이여야 해요.") BigDecimal lng,
        @Size(max = 255, message = "혜택은 255자 이하로 입력해 주세요.") String benefit,
        @Valid @Size(max = 20, message = "소속 혜택이 너무 많아요.") List<AffiliationBenefitRequest> affiliationBenefits,
        @Size(max = 255, message = "주소는 255자 이하로 입력해 주세요.") String address,
        @Size(max = 100, message = "영업시간은 100자 이하로 입력해 주세요.") String hours,
        @Size(max = 50, message = "연락처는 50자 이하로 입력해 주세요.") String contact,
        @Valid MapLinkRequest link
) {
}
