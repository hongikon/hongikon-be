package com.hongmap.hongmapbackend.building;

import com.hongmap.hongmapbackend.building.dto.BuildingListResponse;
import com.hongmap.hongmapbackend.building.dto.BuildingResponse;
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

    @GetMapping("/buildings")
    public BuildingListResponse getAll() {
        return buildingService.getAll();
    }

    @GetMapping("/buildings/{id}")
    public BuildingResponse getById(@PathVariable Long id) {
        return buildingService.getById(id);
    }
}
