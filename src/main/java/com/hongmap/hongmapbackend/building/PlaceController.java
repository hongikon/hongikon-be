package com.hongmap.hongmapbackend.building;

import com.hongmap.hongmapbackend.building.dto.PlaceListResponse;
import com.hongmap.hongmapbackend.building.dto.PlaceResponse;
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

    @GetMapping("/places")
    public PlaceListResponse getFiltered(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String category
    ) {
        return placeService.getFiltered(buildingId, category);
    }

    @GetMapping("/places/{id}")
    public PlaceResponse getById(@PathVariable Long id) {
        return placeService.getById(id);
    }
}
