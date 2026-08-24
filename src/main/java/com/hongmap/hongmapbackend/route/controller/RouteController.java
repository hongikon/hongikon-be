package com.hongmap.hongmapbackend.route.controller;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.route.dto.RoutePathResponse;
import com.hongmap.hongmapbackend.route.dto.RouteSearchRequest;
import com.hongmap.hongmapbackend.route.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "경로 탐색", description = "출발/도착 접속점의 코드(건물+층 슬러그, 예: hongik_k_floor_1)를 기준으로 캠퍼스 내 최단 경로를 탐색합니다.")
    @PostMapping("/search")
    public ResponseEntity<RoutePathResponse> search(@Valid @RequestBody RouteSearchRequest request) {
        return ResponseEntity.ok(routeService.findShortestPath(request));
    }
}
