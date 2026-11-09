package com.hongmap.hongmapbackend.walkroute;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 앱 지도 길찾기(건물 → 건물). 비로그인 허용 — SecurityConfig. 내 위치에서 출발은 위치를 보내지 않도록 앱이 계산한다. */
@RestController
@RequiredArgsConstructor
public class WalkRouteController {

    private final WalkRouteService walkRouteService;

    public record WalkRoutesResponse(List<WalkRoute> routes) {
    }

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "걷는 길찾기(건물 → 건물)", description = "from·to 는 GET /map/data 의 buildings[].id, fromFloor·toFloor 는 고른 층"
            + "(지상 양수, 지하 음수, 로비 L=0, 1.5 같은 중간층; 안 고르면 생략). 경로망 조사 v3 로 후보를 최대 3개(가장 빠른 길이 맨 앞) 낸다. "
            + "경로망이 그 구간을 못 덮으면 직선 어림 하나(isEstimate=true). 같은 건물이면 400, 없는 건물이면 404.")
    @GetMapping("/map/routes")
    public ResponseEntity<WalkRoutesResponse> routes(@RequestParam long from,
                                                     @RequestParam(required = false) Double fromFloor,
                                                     @RequestParam long to,
                                                     @RequestParam(required = false) Double toFloor) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(new WalkRoutesResponse(walkRouteService.routes(from, fromFloor, to, toFloor)));
    }

    @Tag(name = SwaggerConfig.TAG_MAP_NAVIGATION)
    @Operation(summary = "내 위치에서 출발하는 길(위치는 보내지 않음)", description = "to·toFloor 는 /map/routes 와 같다. 내 위치를 받지 않고, "
            + "모든 실외 길 점(starts[].node)에서 도착까지의 가장 빠른 길(points 제외)과 길 점 목록 nodes[{id,lat,lng,next}](next 를 따라가면 도착)을 낸다. "
            + "앱은 내 위치에서 150m 안 가까운 길 점 3곳까지 걷는 시간을 더해 가장 빠른 것을 고르고, 점 목록은 nodes 로 만든다. "
            + "경로망이 도착 건물을 못 덮으면 starts 가 비어 있다(앱이 직선 어림).")
    @GetMapping("/map/routes/approach")
    public ResponseEntity<WalkRouter.Approach> approach(@RequestParam long to, @RequestParam(required = false) Double toFloor) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(walkRouteService.approach(to, toFloor));
    }
}
