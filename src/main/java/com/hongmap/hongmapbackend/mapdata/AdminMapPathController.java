package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathEdge;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathEdgeListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathEdgeRequest;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathNode;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathNodeListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathNodeRequest;
import com.hongmap.hongmapbackend.mapdata.dto.PathAuditResponse;
import com.hongmap.hongmapbackend.mapdata.dto.PathImportReport;
import com.hongmap.hongmapbackend.mapdata.dto.PathImportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지도 경로망(길찾기용 점·간선) 관리. ADMIN 전용 — SecurityConfig 의 /admin/** 규칙, 접속 기록은 AdminAuditInterceptor.
 * 쓰기가 성공하면 GET /map/data 의 paths 가 바로 새 값을 준다. 경로 계산은 앱이 한다(서버 계산 API 없음).
 */
@Slf4j
@Tag(name = SwaggerConfig.TAG_ADMIN)
@RestController
@RequestMapping("/admin/map")
@RequiredArgsConstructor
public class AdminMapPathController {

    private final AdminMapPathService adminMapPathService;
    private final PathNetworkImportService pathNetworkImportService;

    @Operation(summary = "경로 점 목록", description = "code 순. 출입구 노드의 lat·lng 는 건물 entrances 에서 읽은 값, degree = 이어진 간선 수.")
    @GetMapping("/path-nodes")
    public AdminPathNodeListResponse pathNodes() {
        return adminMapPathService.nodes();
    }

    @Operation(summary = "경로 점 추가",
            description = "WAYPOINT: lat·lng 필수, id 를 비우면 pn-xxxxxxxx. ENTRANCE: buildingCode·entranceLabel 필수, "
                    + "id 는 e-{건물 code}-{라벨}. 없는 건물·라벨 400, 같은 id·같은 출입구 노드 409.")
    @PostMapping("/path-nodes")
    public ResponseEntity<AdminPathNode> createPathNode(@AuthenticationPrincipal Long adminId,
                                                        @Valid @RequestBody AdminPathNodeRequest request) {
        AdminPathNode saved = adminMapPathService.createNode(request);
        log.info("경로 점 추가: adminId={}, code={}", adminId, saved.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "경로 점 수정", description = "중간점(WAYPOINT) 좌표만 바꾼다. 출입구 노드는 400(새로 만들고 간선을 옮긴다).")
    @PutMapping("/path-nodes/{code}")
    public AdminPathNode updatePathNode(@AuthenticationPrincipal Long adminId, @PathVariable String code,
                                       @Valid @RequestBody AdminPathNodeRequest request) {
        AdminPathNode saved = adminMapPathService.updateNode(code, request);
        log.info("경로 점 수정: adminId={}, code={}", adminId, code);
        return saved;
    }

    @Operation(summary = "경로 점 삭제", description = "이어진 간선이 있으면 409 — 간선을 먼저 지운다.")
    @DeleteMapping("/path-nodes/{code}")
    public ResponseEntity<Void> deletePathNode(@AuthenticationPrincipal Long adminId, @PathVariable String code) {
        adminMapPathService.deleteNode(code);
        log.info("경로 점 삭제: adminId={}, code={}", adminId, code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "경로 간선 목록", description = "a·b 는 점 id, lengthM 은 좌표로 계산한 길이(m).")
    @GetMapping("/path-edges")
    public AdminPathEdgeListResponse pathEdges() {
        return adminMapPathService.edges();
    }

    @Operation(summary = "경로 간선 추가", description = "양방향. 같은 점 400, 없는 점 400, 이미 이어진 두 점(방향 무관) 409.")
    @PostMapping("/path-edges")
    public ResponseEntity<AdminPathEdge> createPathEdge(@AuthenticationPrincipal Long adminId,
                                                        @Valid @RequestBody AdminPathEdgeRequest request) {
        AdminPathEdge saved = adminMapPathService.createEdge(request);
        log.info("경로 간선 추가: adminId={}, id={}", adminId, saved.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "경로 간선 삭제")
    @DeleteMapping("/path-edges/{id}")
    public ResponseEntity<Void> deletePathEdge(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        adminMapPathService.deleteEdge(id);
        log.info("경로 간선 삭제: adminId={}, id={}", adminId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "경로망 점검",
            description = "깨진 출입구 참조, 고립된 점, 본망과 끊긴 덩어리, 경로에 없는 출입구, 100m 넘는 간선.")
    @GetMapping("/path-audit")
    public PathAuditResponse pathAudit() {
        return adminMapPathService.audit();
    }

    @Operation(summary = "경로망 임포트",
            description = "앱이 내보낸 format 1 JSON(nodes·edges·entranceRefs). dryRun(기본 true)이면 검증 리포트만. "
                    + "dryRun=false 는 오류가 하나도 없을 때만 기존 경로망을 한 트랜잭션에서 통째로 바꾼다 — 오류가 있으면 400 + 리포트.")
    @PostMapping("/path-network/import")
    public ResponseEntity<PathImportReport> importPathNetwork(@AuthenticationPrincipal Long adminId,
                                                              @RequestParam(defaultValue = "true") boolean dryRun,
                                                              @Valid @RequestBody PathImportRequest request) {
        PathImportReport report = pathNetworkImportService.importNetwork(request, dryRun);
        log.info("경로망 임포트: adminId={}, dryRun={}, applied={}, waypoints={}, entranceNodes={}, edges={}, errors={}",
                adminId, dryRun, report.applied(), report.counts().waypoints(), report.counts().entranceNodes(),
                report.counts().edges(), report.errors().size());
        if (!dryRun && !report.applied()) {
            return ResponseEntity.badRequest().body(report);
        }
        return ResponseEntity.ok(report);
    }
}
