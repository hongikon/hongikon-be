package com.hongmap.hongmapbackend.walkroute;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
            + "(그 건물 층 다이얼에 있는 층만 — 지상 양수, 지하 음수, 1.5 같은 중간층; 안 고르면 생략, 아니면 400). 경로망 조사 v3 로 후보를 최대 3개(가장 빠른 길이 맨 앞) 낸다. "
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
            + "모든 실외 길 점(starts[].node = nodes 번호)에서 도착까지의 후보 최대 3개(routes[].route, points 제외)와 지나는 길 점 번호(routes[].path)를 낸다. "
            + "앱은 내 위치에서 150m 안 가까운 길 점 3곳 중 걷는 시간 + 가장 빠른 길이 가장 작은 점의 후보를 보여 준다. 층은 그 건물에서 고를 수 있는 층만(아니면 400). "
            + "계산이 밀리면 503. "
            + "경로망이 도착 건물을 못 덮으면 starts 가 비어 있다(앱이 직선 어림).")
    @GetMapping(value = "/map/routes/approach", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> approach(@RequestParam long to, @RequestParam(required = false) Double toFloor) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .contentType(MediaType.APPLICATION_JSON)
                .body(walkRouteService.approachJson(to, toFloor));
    }
}
