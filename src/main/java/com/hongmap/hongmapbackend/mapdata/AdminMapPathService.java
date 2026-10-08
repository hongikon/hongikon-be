package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathEdge;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathEdgeListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathEdgeRequest;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathNode;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathNodeListResponse;
import com.hongmap.hongmapbackend.mapdata.dto.AdminPathNodeRequest;
import com.hongmap.hongmapbackend.mapdata.dto.PathAuditResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 경로망 편집(/admin/map/path-nodes·path-edges)과 점검(/admin/map/path-audit).
 * 쓰기가 커밋되면 GET /map/data 캐시를 바로 비운다. 실패하면 400/404/409 + ErrorResponse(message).
 *
 * 출입구(buildings.entrances) 편집 API 는 아직 없다 — 경로가 참조하는 라벨이 동기화 SQL 등으로 바뀌면
 * path-audit 의 brokenEntranceRefs 와 db/create_path_network_tables.sql 의 확인 쿼리로 찾는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMapPathService {

    /** path-audit 에서 '긴 간선'으로 보는 길이(m). 캠퍼스 경로망 간선은 대부분 수십 m 다. */
    public static final double LONG_EDGE_METERS = 100.0;
    /** path-audit 덩어리마다 보여 줄 점 수. */
    static final int COMPONENT_SAMPLE = 20;
    private static final int CODE_ATTEMPTS = 5;

    private final PathNodeRepository nodeRepository;
    private final PathEdgeRepository edgeRepository;
    private final BuildingRepository buildingRepository;
    private final BuildingEntrances buildingEntrances;
    private final MapDataService mapDataService;

    // ── 점 ───────────────────────────────────────────────

    public AdminPathNodeListResponse nodes() {
        PathNetwork network = network();
        Map<String, Integer> degree = network.degrees();
        return new AdminPathNodeListResponse(network.nodes().stream()
                .map(n -> toAdmin(n, degree.getOrDefault(n.code(), 0)))
                .toList());
    }

    @Transactional
    public AdminPathNode createNode(AdminPathNodeRequest request) {
        PathNodeKind kind = kind(request.kind());
        PathNode saved = kind == PathNodeKind.WAYPOINT ? createWaypoint(request) : createEntrance(request);
        mapDataService.invalidateAfterCommit();
        return toAdmin(PathNetwork.resolve(saved, buildingEntrances::parse), 0);
    }

    private PathNode createWaypoint(AdminPathNodeRequest r) {
        if (blankToNull(r.buildingCode()) != null || blankToNull(r.entranceLabel()) != null) {
            throw badRequest("중간점에는 건물·출입구를 넣지 않아요.");
        }
        requireCoords(r);
        String code = newCode(r.id());
        return nodeRepository.save(PathNode.waypoint(code, coord(r.lat()), coord(r.lng())));
    }

    private PathNode createEntrance(AdminPathNodeRequest r) {
        if (r.lat() != null || r.lng() != null) {
            throw badRequest("출입구 노드의 좌표는 건물 출입구에서 읽어요 — 위도·경도를 비워 주세요.");
        }
        String buildingCode = blankToNull(r.buildingCode());
        String label = blankToNull(r.entranceLabel());
        if (buildingCode == null || label == null) {
            throw badRequest("출입구 노드는 건물과 출입구 라벨을 골라 주세요.");
        }
        Building building = buildingRepository.findByCode(buildingCode)
                .orElseThrow(() -> badRequest("존재하지 않는 건물이에요."));
        if (buildingEntrances.parse(building).find(label) == null) {
            throw badRequest("그 건물에 없는 출입구 라벨이에요.");
        }
        String code = MapCodes.pathEntrance(building.getCode(), label);
        String requested = blankToNull(r.id());
        if (requested != null && !requested.equals(code)) {
            throw badRequest("출입구 노드 id 는 " + code + " 로 정해져요 — 비우거나 같은 값을 넣어 주세요.");
        }
        if (nodeRepository.existsByBuildingIdAndEntranceLabel(building.getId(), label)) {
            throw conflict("이 출입구의 노드가 이미 있어요.");
        }
        if (nodeRepository.existsByCode(code)) {
            throw conflict("이미 있는 id 예요.");
        }
        return nodeRepository.save(PathNode.entrance(code, building, label));
    }

    /** 중간점 좌표만 바꾼다. 출입구 노드는 바꿀 수 없다(새로 만들고 간선을 옮긴다). */
    @Transactional
    public AdminPathNode updateNode(String code, AdminPathNodeRequest request) {
        PathNode node = nodeRepository.findByCodeWithBuilding(code)
                .orElseThrow(() -> notFound("존재하지 않는 경로 점이에요."));
        if (node.getKind() == PathNodeKind.ENTRANCE) {
            throw badRequest("출입구 노드는 고칠 수 없어요 — 새로 만들고 간선을 옮겨 주세요.");
        }
        if (kind(request.kind()) != PathNodeKind.WAYPOINT) {
            throw badRequest("중간점을 출입구 노드로 바꿀 수 없어요 — 새로 만들어 주세요.");
        }
        if (blankToNull(request.buildingCode()) != null || blankToNull(request.entranceLabel()) != null) {
            throw badRequest("중간점에는 건물·출입구를 넣지 않아요.");
        }
        requireCoords(request);
        node.moveTo(coord(request.lat()), coord(request.lng()));
        nodeRepository.flush();
        mapDataService.invalidateAfterCommit();
        return toAdmin(PathNetwork.resolve(node, buildingEntrances::parse), (int) edgeRepository.countByNode(node));
    }

    /** 이어진 간선이 있으면 409 — 간선을 먼저 지운다. */
    @Transactional
    public void deleteNode(String code) {
        PathNode node = nodeRepository.findByCodeWithBuilding(code)
                .orElseThrow(() -> notFound("존재하지 않는 경로 점이에요."));
        long edges = edgeRepository.countByNode(node);
        if (edges > 0) {
            throw conflict("이어진 간선 " + edges + "개를 먼저 지워 주세요.");
        }
        nodeRepository.delete(node);
        mapDataService.invalidateAfterCommit();
    }

    // ── 간선 ─────────────────────────────────────────────

    public AdminPathEdgeListResponse edges() {
        PathNetwork network = network();
        return new AdminPathEdgeListResponse(network.edges().stream().map(e -> toAdmin(network, e)).toList());
    }

    @Transactional
    public AdminPathEdge createEdge(AdminPathEdgeRequest request) {
        String a = request.a().trim();
        String b = request.b().trim();
        if (a.equals(b)) {
            throw badRequest("같은 점끼리는 이을 수 없어요.");
        }
        PathNode x = nodeRepository.findByCodeWithBuilding(a).orElseThrow(() -> badRequest("존재하지 않는 경로 점이에요: " + a));
        PathNode y = nodeRepository.findByCodeWithBuilding(b).orElseThrow(() -> badRequest("존재하지 않는 경로 점이에요: " + b));
        PathEdge edge = PathEdge.between(x, y);
        if (edgeRepository.existsByNodeAAndNodeB(edge.getNodeA(), edge.getNodeB())) {
            throw conflict("이미 이어진 두 점이에요.");
        }
        PathEdge saved = edgeRepository.save(edge);
        mapDataService.invalidateAfterCommit();
        PathNetwork.Node na = PathNetwork.resolve(saved.getNodeA(), buildingEntrances::parse);
        PathNetwork.Node nb = PathNetwork.resolve(saved.getNodeB(), buildingEntrances::parse);
        Double length = na.broken() || nb.broken() ? null : PathGeo.roundMeters(PathGeo.distanceMeters(na.point(), nb.point()));
        return new AdminPathEdge(saved.getId(), na.code(), nb.code(), length);
    }

    @Transactional
    public void deleteEdge(Long id) {
        PathEdge edge = edgeRepository.findById(id).orElseThrow(() -> notFound("존재하지 않는 간선이에요."));
        edgeRepository.delete(edge);
        mapDataService.invalidateAfterCommit();
    }

    // ── 점검 ─────────────────────────────────────────────

    public PathAuditResponse audit() {
        List<Building> buildings = buildingRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, BuildingEntrances.Parsed> entrances = buildingEntrances.index(buildings);
        PathNetwork network = PathNetwork.of(nodeRepository.findAllWithBuilding(), edgeRepository.findAllWithNodes(),
                b -> entrances.computeIfAbsent(b.getId(), id -> buildingEntrances.parse(b)));

        List<PathAuditResponse.BrokenEntranceRef> broken = network.brokenNodes().stream()
                .map(n -> new PathAuditResponse.BrokenEntranceRef(n.code(), n.buildingCode(), n.entranceLabel(), n.brokenReason()))
                .toList();

        Map<String, Integer> degree = network.degrees();
        List<String> isolated = network.nodes().stream()
                .filter(n -> degree.getOrDefault(n.code(), 0) == 0).map(PathNetwork.Node::code).toList();

        List<List<String>> components = network.components();
        List<PathAuditResponse.Component> detached = new ArrayList<>();
        for (int i = 1; i < components.size(); i++) {
            List<String> c = components.get(i);
            if (c.size() > 1) {   // 크기 1 은 isolatedNodes 에 이미 있다
                detached.add(new PathAuditResponse.Component(c.size(), c.subList(0, Math.min(c.size(), COMPONENT_SAMPLE))));
            }
        }

        Set<String> linked = new HashSet<>();
        for (PathNetwork.Node n : network.nodes()) {
            if (n.kind() == PathNodeKind.ENTRANCE) {
                linked.add(n.buildingCode() + "\u0000" + n.entranceLabel());
            }
        }
        List<PathAuditResponse.EntranceRef> unlinked = new ArrayList<>();
        for (Building b : buildings) {
            for (String label : entrances.get(b.getId()).byLabel().keySet()) {
                if (!linked.contains(b.getCode() + "\u0000" + label)) {
                    unlinked.add(new PathAuditResponse.EntranceRef(b.getCode(), label));
                }
            }
        }

        List<AdminPathEdge> longEdges = new ArrayList<>();
        for (PathNetwork.Edge e : network.edges()) {
            Double length = network.lengthMeters(e);
            if (length != null && length > LONG_EDGE_METERS) {
                longEdges.add(toAdmin(network, e));
            }
        }

        PathAuditResponse.Summary summary = new PathAuditResponse.Summary(network.nodes().size(), network.edges().size(),
                components.size(), components.isEmpty() ? 0 : components.get(0).size());
        return new PathAuditResponse(summary, broken, isolated, detached, unlinked, LONG_EDGE_METERS, longEdges);
    }

    // ── 공통 ─────────────────────────────────────────────

    private PathNetwork network() {
        return PathNetwork.of(nodeRepository.findAllWithBuilding(), edgeRepository.findAllWithNodes(), buildingEntrances::parse);
    }

    private static AdminPathNode toAdmin(PathNetwork.Node n, int degree) {
        return new AdminPathNode(n.code(), n.kind().name(),
                n.point() == null ? null : n.point().lat(), n.point() == null ? null : n.point().lng(),
                n.buildingCode(), n.entranceLabel(), degree, n.broken());
    }

    private static AdminPathEdge toAdmin(PathNetwork network, PathNetwork.Edge e) {
        Double length = network.lengthMeters(e);
        return new AdminPathEdge(e.id(), e.a(), e.b(), length == null ? null : PathGeo.roundMeters(length));
    }

    static PathNodeKind kind(String raw) {
        try {
            return PathNodeKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw badRequest("종류는 WAYPOINT 또는 ENTRANCE 예요.");
        }
    }

    private static void requireCoords(AdminPathNodeRequest r) {
        if (r.lat() == null || r.lng() == null) {
            throw badRequest("중간점은 위도와 경도를 함께 입력해 주세요.");
        }
    }

    /** 요청 id 가 있으면 그대로(이미 있으면 409), 없으면 pn- + 무작위 8자(겹치면 다시). */
    private String newCode(String requested) {
        String code = blankToNull(requested);
        if (code != null) {
            if (nodeRepository.existsByCode(code)) {
                throw conflict("이미 있는 id 예요.");
            }
            return code;
        }
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            String generated = MapCodes.generate(MapCodes.PATH_NODE_PREFIX);
            if (!nodeRepository.existsByCode(generated)) {
                return generated;
            }
        }
        throw conflict("id 를 만들지 못했어요. 다시 시도해 주세요.");
    }

    /** DECIMAL(10,7) 에 맞춰 소수 7자리로. */
    static BigDecimal coord(BigDecimal value) {
        return value == null ? null : value.setScale(7, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
