package com.hongmap.hongmapbackend.building;

import com.hongmap.hongmapbackend.building.dto.PlaceListResponse;
import com.hongmap.hongmapbackend.building.dto.PlaceResponse;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지도 조회 범주 — 전부 게스트 허용.
 * SecurityConfig의 permitAll()에 GET /places, GET /places/** 등록 필요.
 */
@RestController
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "시설 목록 조회", description = "건물 id 또는 카테고리로 필터링된 시설(강의실, 식당, 편의시설 등) 목록을 조회합니다.")
    @GetMapping("/places")
    public PlaceListResponse getFiltered(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String category
    ) {
        return placeService.getFiltered(buildingId, category);
    }

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "시설 상세 조회", description = "시설 id로 특정 시설의 상세 정보를 조회합니다.")
    @GetMapping("/places/{id}")
    public PlaceResponse getById(@PathVariable Long id) {
        return placeService.getById(id);
    }
}
