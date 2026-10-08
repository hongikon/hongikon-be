package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.mapdata.dto.PathAuditResponse;
import com.hongmap.hongmapbackend.mapdata.dto.PathImportReport;
import com.hongmap.hongmapbackend.mapdata.dto.PathImportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 경로망 임포트(POST /admin/map/path-network/import). 앱이 내보낸 format 1 JSON 을 검증해 리포트를 만들고,
 * dryRun 이 아니고 오류가 하나도 없을 때만 경로망 전체를 한 트랜잭션에서 바꾼다(기존 점·간선을 지우고 새로 넣음 — 전부 아니면 전무).
 * 적용하면 커밋 직후 GET /map/data 캐시를 비운다.
 *
 * - 점 id 는 프론트 id 그대로. MapDataRules.CODE_REGEX 에 맞지 않는 것만 MapCodes.toCode 로 바꾸고(겹치면 -2, -3…) 리포트에 남긴다.
 * - 출입구 참조는 buildingCode 가 있으면 그것으로, 없으면 이름을 display_name → name 순으로 찾는다(공백 정리 후 정확히 일치).
 *   라벨은 그 건물 entrances 에 정확히 있어야 한다. 출입구 노드 id 는 MapCodes.pathEntrance(건물 code, 라벨).
 */
@Service
@RequiredArgsConstructor
public class PathNetworkImportService {

    public static final int FORMAT = 1;
    static final int CANDIDATE_LIMIT = 20;
    private static final Pattern CODE = Pattern.compile(MapDataRules.CODE_REGEX);
    private static final BigDecimal LAT_MIN = new BigDecimal("33");
    private static final BigDecimal LAT_MAX = new BigDecimal("39");
    private static final BigDecimal LNG_MIN = new BigDecimal("124");
    private static final BigDecimal LNG_MAX = new BigDecimal("132");

    private final PathNodeRepository nodeRepository;
    private final PathEdgeRepository edgeRepository;
    private final BuildingRepository buildingRepository;
    private final BuildingEntrances buildingEntrances;
    private final MapDataService mapDataService;

    @Transactional
    public PathImportReport importNetwork(PathImportRequest request, boolean dryRun) {
        Plan plan = new Plan(request);
        if (request.format() == null || request.format() != FORMAT) {
            plan.error("FORMAT", "format", "format " + FORMAT + " 만 읽을 수 있어요.", null);
            return plan.report(dryRun, false);
        }
        plan.build();
        if (dryRun || !plan.errors.isEmpty()) {
            return plan.report(dryRun, false);
        }
        apply(plan);
        mapDataService.invalidateAfterCommit();
        return plan.report(false, true);
    }

    private void apply(Plan plan) {
        edgeRepository.deleteAllInBatch();
        nodeRepository.deleteAllInBatch();
        Map<String, PathNode> saved = new HashMap<>();
        plan.waypoints.forEach((code, n) -> saved.put(code, nodeRepository.save(
                PathNode.waypoint(code, AdminMapPathService.coord(n.lat()), AdminMapPathService.coord(n.lng())))));
        plan.usedEntrances().forEach((code, t) -> saved.put(code, nodeRepository.save(
                PathNode.entrance(code, t.building(), t.label()))));
        for (List<String> e : plan.edges) {
            edgeRepository.save(PathEdge.between(saved.get(e.get(0)), saved.get(e.get(1))));
        }
        edgeRepository.flush();
    }

    private record EntranceTarget(Building building, String label, PathGeo.Point point) {
    }

    /** 참조 하나를 찾은 결과. 실패했으면 nodeCode 가 null(오류는 이미 기록). */
    private record RefResult(String nodeCode, PathImportReport.EntranceRef resolved) {
    }

    /** 요청 하나를 검증하며 넣을 내용을 만든다. */
    private final class Plan {

        final PathImportRequest request;
        final List<PathImportReport.Issue> errors = new ArrayList<>();
        final List<PathImportReport.IdConversion> conversions = new ArrayList<>();
        /** code → 원래 점(좌표). 입력 순서 유지. */
        final Map<String, PathImportRequest.Node> waypoints = new LinkedHashMap<>();
        /** 입력 id → code. */
        final Map<String, String> idToCode = new HashMap<>();
        /** 출입구 노드 code → 대상. */
        final Map<String, EntranceTarget> entrances = new LinkedHashMap<>();
        /** 참조 문자열 → 결과(입력 순서). */
        final Map<String, RefResult> refs = new LinkedHashMap<>();
        final Set<String> usedRefs = new HashSet<>();
        final Set<String> usedEntranceCodes = new LinkedHashSet<>();
        /** 간선 [작은 code, 큰 code]. */
        final List<List<String>> edges = new ArrayList<>();

        List<Building> buildings;
        Map<Long, BuildingEntrances.Parsed> entranceIndex;

        Plan(PathImportRequest request) {
            this.request = request;
        }

        void error(String type, String target, String message, List<String> candidates) {
            errors.add(new PathImportReport.Issue(type, target, message, candidates));
        }

        void build() {
            buildings = buildingRepository.findAllByOrderBySortOrderAscIdAsc();
            entranceIndex = buildingEntrances.index(buildings);
            readNodes();
            if (request.entranceRefs() != null) {
                for (PathImportRequest.EntranceRef r : request.entranceRefs()) {
                    if (r == null || blank(r.ref())) {
                        error("INVALID_REF", "entranceRefs", "ref 가 비어 있어요.", null);
                        continue;
                    }
                    String ref = r.ref().trim();
                    if (refs.containsKey(ref)) {
                        continue;   // 같은 참조가 두 번 — 앞의 것
                    }
                    refs.put(ref, resolve(ref, r.buildingName(), r.buildingCode(), r.label()));
                }
            }
            readEdges();
        }

        private void readNodes() {
            List<PathImportRequest.Node> nodes = request.nodes() == null ? List.of() : request.nodes();
            Set<String> seen = new HashSet<>();
            Set<String> taken = new HashSet<>();
            for (PathImportRequest.Node n : nodes) {   // 규칙에 맞는 id 를 먼저 잡아 둔다 — 바꾼 id 가 이것과 겹치지 않게
                if (n != null && n.id() != null && CODE.matcher(n.id()).matches()) {
                    taken.add(n.id());
                }
            }
            for (int i = 0; i < nodes.size(); i++) {
                PathImportRequest.Node n = nodes.get(i);
                if (n == null || blank(n.id())) {
                    error("INVALID_NODE", "nodes[" + i + "]", "점 id 가 비어 있어요.", null);
                    continue;
                }
                String raw = n.id();
                if (!seen.add(raw)) {
                    error("DUPLICATE_NODE_ID", raw, "같은 점 id 가 두 번 있어요.", null);
                    continue;
                }
                if (!inRange(n.lat(), LAT_MIN, LAT_MAX) || !inRange(n.lng(), LNG_MIN, LNG_MAX)) {
                    error("INVALID_COORD", raw, "좌표가 없거나 범위(위도 33~39, 경도 124~132) 밖이에요.", null);
                }
                String code = raw;
                if (!CODE.matcher(raw).matches()) {
                    String base = MapCodes.toCode(raw);
                    code = base;
                    for (int k = 2; taken.contains(code); k++) {
                        code = base + "-" + k;
                    }
                    taken.add(code);
                    conversions.add(new PathImportReport.IdConversion(raw, code));
                }
                idToCode.put(raw, code);
                waypoints.put(code, n);
            }
        }

        private RefResult resolve(String ref, String buildingName, String buildingCode, String label) {
            int hash = ref.lastIndexOf('#');
            String name = !blank(buildingName) ? buildingName : hash > 0 ? ref.substring(0, hash) : null;
            String lab = !blank(label) ? label.trim() : hash >= 0 ? ref.substring(hash + 1).trim() : null;
            if (blank(lab) || (blank(buildingCode) && blank(name))) {
                error("INVALID_REF", ref, "'건물명#라벨' 형식이 아니에요.", null);
                return new RefResult(null, null);
            }

            Building building;
            String matchedBy;
            if (!blank(buildingCode)) {
                String code = buildingCode.trim();
                building = buildings.stream().filter(b -> code.equals(b.getCode())).findFirst().orElse(null);
                matchedBy = "buildingCode";
                if (building == null) {
                    error("BUILDING_NOT_FOUND", ref, "buildingCode '" + code + "' 인 건물이 없어요.", null);
                    return new RefResult(null, null);
                }
            } else {
                String wanted = normalize(name);
                List<Building> byDisplay = buildings.stream()
                        .filter(b -> b.getDisplayName() != null && normalize(b.getDisplayName()).equals(wanted)).toList();
                List<Building> byName = buildings.stream().filter(b -> normalize(b.getName()).equals(wanted)).toList();
                List<Building> matches = !byDisplay.isEmpty() ? byDisplay : byName;
                matchedBy = !byDisplay.isEmpty() ? "displayName" : "name";
                if (matches.isEmpty()) {
                    error("BUILDING_NOT_FOUND", ref, "'" + wanted + "' 인 건물이 없어요(display_name·name).", null);
                    return new RefResult(null, null);
                }
                if (matches.size() > 1) {
                    error("BUILDING_AMBIGUOUS", ref, "'" + wanted + "' 인 건물이 여러 개예요 — buildingCode 를 넣어 주세요.",
                            matches.stream().map(Building::getCode).toList());
                    return new RefResult(null, null);
                }
                building = matches.get(0);
            }

            BuildingEntrances.Parsed parsed = entranceIndex.get(building.getId());
            PathGeo.Point point = parsed.find(lab);
            if (point == null) {
                String l = lab;
                List<String> labels = new ArrayList<>(parsed.byLabel().keySet());
                labels.sort(Comparator.<String>comparingInt(x -> x.equalsIgnoreCase(l) ? 0 : 1).thenComparing(x -> x));
                error("LABEL_NOT_FOUND", ref, parsed.invalid()
                                ? building.getCode() + " 의 entrances 를 읽을 수 없어요."
                                : building.getCode() + " 에 라벨 '" + lab + "' 인 출입구가 없어요.",
                        labels.subList(0, Math.min(labels.size(), CANDIDATE_LIMIT)));
                return new RefResult(null, null);
            }

            String nodeCode = MapCodes.pathEntrance(building.getCode(), lab);
            if (waypoints.containsKey(nodeCode)) {
                error("DUPLICATE_NODE_ID", nodeCode, "출입구 노드 id 가 점 id 와 겹쳐요.", null);
                return new RefResult(null, null);
            }
            entrances.putIfAbsent(nodeCode, new EntranceTarget(building, lab, point));
            return new RefResult(nodeCode,
                    new PathImportReport.EntranceRef(ref, building.getCode(), lab, nodeCode, matchedBy));
        }

        private void readEdges() {
            List<List<String>> list = request.edges() == null ? List.of() : request.edges();
            Set<String> pairs = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                String target = "edges[" + i + "]";
                List<String> e = list.get(i);
                if (e == null || e.size() != 2 || blank(e.get(0)) || blank(e.get(1))) {
                    error("INVALID_EDGE", target, "간선은 [점, 점] 두 개여야 해요.", null);
                    continue;
                }
                String a = endpoint(e.get(0).trim(), target);
                String b = endpoint(e.get(1).trim(), target);
                if (a == null || b == null) {
                    continue;   // 오류는 endpoint 가 기록(참조 실패는 참조 쪽에 한 번만)
                }
                if (a.equals(b)) {
                    error("SELF_LOOP", target, "같은 점끼리 잇는 간선이에요: " + a, null);
                    continue;
                }
                List<String> pair = a.compareTo(b) < 0 ? List.of(a, b) : List.of(b, a);
                if (!pairs.add(pair.get(0) + "\u0000" + pair.get(1))) {
                    error("DUPLICATE_EDGE", target, "이미 있는 간선이에요(방향 무관): " + pair.get(0) + " – " + pair.get(1), null);
                    continue;
                }
                edges.add(pair);
                for (String code : pair) {
                    if (entrances.containsKey(code)) {
                        usedEntranceCodes.add(code);
                    }
                }
            }
        }

        /** 간선 끝점 → code. 점 id, 등록된 참조, 그 밖의 '건물명#라벨' 순. 못 찾으면 null. */
        private String endpoint(String raw, String target) {
            String code = idToCode.get(raw);
            if (code != null) {
                return code;
            }
            RefResult ref = refs.get(raw);
            if (ref == null && raw.indexOf('#') > 0) {
                ref = resolve(raw, null, null, null);
                refs.put(raw, ref);
            }
            if (ref != null) {
                usedRefs.add(raw);
                return ref.nodeCode();
            }
            error("EDGE_UNKNOWN_NODE", target, "점 id 나 출입구 참조가 아니에요: " + raw, null);
            return null;
        }

        Map<String, EntranceTarget> usedEntrances() {
            Map<String, EntranceTarget> used = new LinkedHashMap<>();
            for (String code : usedEntranceCodes) {
                used.put(code, entrances.get(code));
            }
            return used;
        }

        PathImportReport report(boolean dryRun, boolean applied) {
            List<PathImportReport.EntranceRef> resolved = refs.values().stream()
                    .filter(r -> r.resolved() != null).map(RefResult::resolved).toList();
            List<String> unused = refs.keySet().stream().filter(r -> !usedRefs.contains(r)).toList();

            List<PathNetwork.Node> nodes = new ArrayList<>();
            waypoints.forEach((code, n) -> nodes.add(new PathNetwork.Node(code, PathNodeKind.WAYPOINT, null, null,
                    n.lat() == null || n.lng() == null ? null : new PathGeo.Point(n.lat().doubleValue(), n.lng().doubleValue()),
                    null)));
            usedEntrances().forEach((code, t) -> nodes.add(new PathNetwork.Node(code, PathNodeKind.ENTRANCE,
                    t.building().getCode(), t.label(), t.point(), null)));
            PathNetwork network = new PathNetwork(nodes,
                    edges.stream().map(p -> new PathNetwork.Edge(null, p.get(0), p.get(1))).toList());
            Map<String, Integer> degree = network.degrees();
            List<String> isolated = network.nodes().stream()
                    .filter(n -> degree.getOrDefault(n.code(), 0) == 0).map(PathNetwork.Node::code).toList();
            List<List<String>> components = network.components();
            List<PathAuditResponse.Component> detached = new ArrayList<>();
            for (int i = 1; i < components.size(); i++) {
                List<String> c = components.get(i);
                if (c.size() > 1) {
                    detached.add(new PathAuditResponse.Component(c.size(),
                            c.subList(0, Math.min(c.size(), AdminMapPathService.COMPONENT_SAMPLE))));
                }
            }

            return new PathImportReport(dryRun, applied, request.format(), request.source(),
                    new PathImportReport.Counts(waypoints.size(), usedEntranceCodes.size(), edges.size()),
                    conversions, resolved, errors,
                    new PathImportReport.Warnings(isolated, detached, unused));
        }
    }

    private static boolean inRange(BigDecimal v, BigDecimal min, BigDecimal max) {
        return v != null && v.compareTo(min) >= 0 && v.compareTo(max) <= 0;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
