package com.hongmap.hongmapbackend.walkroute;

import com.hongmap.hongmapbackend.common.devkey.DevKeyGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Hidden;

import java.util.List;

/**
 * 경로 조사 도구(/dev/path-survey-v3)용: 경로망 올리기(초안·검사·비교) → 길 시험(초안과 운영 나란히) → 운영에 적용·되돌리기, 백업 다시 불러오기.
 * 개발용 암호키(X-Dev-Key, DevKeyGuard)만 확인한다(11-10 사용자 결정) — 로그인은 보지 않는다(SecurityConfig permitAll). 키가 없는 서버에서는 404.
 */
@Hidden
@RestController
@RequestMapping("/map/route-graph")
@RequiredArgsConstructor
public class RouteGraphController {

    private final DevKeyGuard devKeyGuard;
    private final WalkGraphService walkGraphService;
    private final WalkRouteService walkRouteService;

    public record PreviewResponse(List<WalkRoute> version, List<WalkRoute> active) {
    }

    @GetMapping("/versions")
    public WalkGraphService.VersionList list(@RequestHeader(value = DevKeyGuard.HEADER, required = false) String key) {
        devKeyGuard.check(key);
        return walkGraphService.list();
    }

    @PostMapping("/versions")
    public WalkGraphService.VersionView upload(@RequestHeader(value = DevKeyGuard.HEADER, required = false) String key,
                                               @RequestBody WalkGraphService.UploadRequest request) {
        devKeyGuard.check(key);
        return walkGraphService.upload(request);
    }

    @GetMapping("/versions/{id}")
    public WalkGraphService.VersionView get(@RequestHeader(value = DevKeyGuard.HEADER, required = false) String key, @PathVariable long id) {
        devKeyGuard.check(key);
        return walkGraphService.get(id);
    }

    @GetMapping(value = "/versions/{id}/backup", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> backup(@RequestHeader(value = DevKeyGuard.HEADER, required = false) String key, @PathVariable long id) {
        devKeyGuard.check(key);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(walkGraphService.backup(id));
    }

    @PostMapping("/versions/{id}/activate")
    public WalkGraphService.VersionView activate(@RequestHeader(value = DevKeyGuard.HEADER, required = false) String key, @PathVariable long id) {
        devKeyGuard.check(key);
        return walkGraphService.activate(id);
    }

    /** 길 시험: 이 버전 경로망과 지금 운영 경로망으로 같은 출발·도착을 찾아 나란히. */
    @GetMapping("/versions/{id}/routes")
    public PreviewResponse routes(@RequestHeader(value = DevKeyGuard.HEADER, required = false) String key, @PathVariable long id,
                                  @RequestParam long from, @RequestParam(required = false) Double fromFloor,
                                  @RequestParam long to, @RequestParam(required = false) Double toFloor) {
        devKeyGuard.check(key);
        return new PreviewResponse(walkRouteService.previewRoutes(id, from, fromFloor, to, toFloor),
                walkRouteService.routes(from, fromFloor, to, toFloor));
    }
}
