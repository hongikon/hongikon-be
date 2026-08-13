package com.hongmap.hongmapbackend.route.controller;

import com.hongmap.hongmapbackend.route.dto.RoutePathResponse;
import com.hongmap.hongmapbackend.route.dto.RouteSearchRequest;
import com.hongmap.hongmapbackend.route.service.RouteService;
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

    @PostMapping("/search")
    public ResponseEntity<RoutePathResponse> search(@Valid @RequestBody RouteSearchRequest request) {
        return ResponseEntity.ok(routeService.findShortestPath(request));
    }
}
