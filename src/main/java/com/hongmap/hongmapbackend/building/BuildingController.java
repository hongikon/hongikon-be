package com.hongmap.hongmapbackend.building;

import com.hongmap.hongmapbackend.building.dto.BuildingListResponse;
import com.hongmap.hongmapbackend.building.dto.BuildingResponse;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지도 조회 범주 — README 원칙("지도·공지는 비로그인 가능")에 따라 전부 게스트 허용.
 * SecurityConfig의 permitAll()에 GET /buildings, GET /buildings/** 등록 필요.
 */
@RestController
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "건물 전체 목록 조회", description = "캠퍼스 내 모든 건물의 위치·정보 목록을 조회합니다.")
    @GetMapping("/buildings")
    public BuildingListResponse getAll() {
        return buildingService.getAll();
    }

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "건물 상세 조회", description = "건물 id로 특정 건물의 상세 정보를 조회합니다.")
    @GetMapping("/buildings/{id}")
    public BuildingResponse getById(@PathVariable Long id) {
        return buildingService.getById(id);
    }
}
