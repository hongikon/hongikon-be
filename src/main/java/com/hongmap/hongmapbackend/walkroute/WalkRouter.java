package com.hongmap.hongmapbackend.walkroute;

import com.hongmap.hongmapbackend.walkroute.WalkRoute.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 캠퍼스 걷는 길찾기(경로망 조사 v3). 앱 src/utils/routing.ts(11-09)를 그대로 옮겼다 — 같은 경로망·건물이면 같은 후보를 낸다
 * (WalkRouterParityTest 가 앱 결과와 맞춰 본다). 계산 순서(삽입 순서·같은 값일 때 먼저 들어온 쪽)와 수학 함수(fdlibm, StrictMath)도
 * 앱(JavaScript)과 같게 둔다.
 *
 * <p>한 번 만든 뒤에는 읽기만 한다(여러 요청이 같이 써도 된다). 요청마다 기본 그래프를 복사해 출발·도착 점을 붙인다.
 */
final class WalkRouter {

    /** 도보 속도(m/분). */
    static final double WALKING_METERS_PER_MINUTE = 80;
    /** 한 개 층을 오르내리는 시간(초). 계단 수직 간선과 조사되지 않은 층의 출입구 어림에 쓴다. */
    static final double FLOOR_TRANSIT_SECONDS = 20;
    /** 엘리베이터를 한 번 타는 고정 시간(초). */
    static final double ELEVATOR_WAIT_SECONDS = 30;
    /** 엘리베이터가 한 층 움직이는 시간(초). */
    static final double ELEVATOR_SECONDS_PER_FLOOR = 4;
    /** 바깥 계단 구간 시간 = 평지 걷는 시간 × 이 값. */
    private static final double STAIRS_TIME_FACTOR = 3;
    /** 계단 끝에서 이만큼 안의 출입구로 그 자리 높이를 어림한다. */
    private static final double STAIR_ENTRANCE_METERS = 35;
    /** 경로망 밖 건물의 출입구를 이을 가까운 길 점 수와 최대 거리. */
    private static final int LINK_POINTS = 2;
    private static final double LINK_MAX_METERS = 300;
    /** 이미 찾은 길을 이만큼 비싸게 보고 다시 찾는다(벌점 방식). */
    private static final double DETOUR_PENALTY = 1.8;
    private static final int PENALTY_ROUNDS = 4;
    static final int MAX_ALTERNATIVES = 3;
    private static final String MY_LOCATION_NODE = "me";

    private static final Pattern ENTRANCE_CODE = Pattern.compile("^HI_([A-Z0-9]+)_(B?)(\\d+(?:\\.\\d+)?)F?(?:_\\d+)?_ENTER$");
    private static final Pattern ELEVATOR_CODE = Pattern.compile("^HI_([A-Z0-9]+)(?:_\\d+)?_ELEVATOR$");
    private static final Pattern LABEL_PREFIX = Pattern.compile("^HI_([A-Z0-9]+)_");
    private static final Pattern FLOOR_NODE = Pattern.compile("::(-?[\\d.]+)$");

    private final SurveyGraph survey;
    private final List<CampusBuilding> buildings;
    private final Map<String, Double> vertexFloors = new HashMap<>();
    private final Map<String, String> vertexCodes = new HashMap<>();
    private final Map<String, String> entranceLabels = new HashMap<>();
    /** 건물 code → 이름표 글자('T동'). */
    private final Map<String, String> codeTokens = new HashMap<>();
    /** 층이 있는 출입구 정점들(계단 방향 어림용). */
    private final List<EntrancePoint> entrancePoints = new ArrayList<>();
    private final Function<String, CampusBuilding> resolveBuilding;
    private final Graph base;

    private record EntrancePoint(double lat, double lng, String code, double floor) {
    }

    private record GraphEdge(String to, double seconds, double meters, boolean stairs, boolean link, boolean vertical) {
    }

    private record FloorEntrance(String id, double floor) {
    }

    private record PathResult(List<String> nodeIds, double seconds) {
    }

    private static final class Graph {
        final Map<String, Point> nodes;
        /** 층 이동을 나타내는 합성 노드(건물 가운데). */
        final Set<String> floorNodeIds;
        final Map<String, List<GraphEdge>> adj;
        /** 건물 이름 → 경로망에 이어진 출입구(정점 id 와 그 출입구 층). */
        final Map<String, List<FloorEntrance>> entrancesByBuilding;
        /** 건물 이름 → 층 → 그 층에서 조사된 실내 정점 id. */
        final Map<String, Map<Double, List<String>>> levelVertices;
        /** 가장 큰 덩어리(서로 이어진 정점 무리)의 정점. */
        Set<String> mainComponent;

        Graph() {
            this(new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                    new LinkedHashSet<>());
        }

        Graph(Map<String, Point> nodes, Set<String> floorNodeIds, Map<String, List<GraphEdge>> adj,
              Map<String, List<FloorEntrance>> entrancesByBuilding, Map<String, Map<Double, List<String>>> levelVertices,
              Set<String> mainComponent) {
            this.nodes = nodes;
            this.floorNodeIds = floorNodeIds;
            this.adj = adj;
            this.entrancesByBuilding = entrancesByBuilding;
            this.levelVertices = levelVertices;
            this.mainComponent = mainComponent;
        }

        List<GraphEdge> edges(String id) {
            return adj.getOrDefault(id, List.of());
        }

        GraphEdge edge(String from, String to) {
            for (GraphEdge e : edges(from)) {
                if (e.to().equals(to)) {
                    return e;
                }
            }
            return null;
        }

        void push(String id, GraphEdge edge) {
            adj.computeIfAbsent(id, k -> new ArrayList<>()).add(edge);
        }

        /** 요청마다 쓰는 복사본. 출입구·층 정점·덩어리는 바뀌지 않아 함께 쓴다. */
        Graph copy() {
            Map<String, List<GraphEdge>> adjCopy = new LinkedHashMap<>();
            adj.forEach((id, list) -> adjCopy.put(id, new ArrayList<>(list)));
            return new Graph(new LinkedHashMap<>(nodes), new LinkedHashSet<>(floorNodeIds), adjCopy, entrancesByBuilding,
                    levelVertices, mainComponent);
        }
    }

    WalkRouter(SurveyGraph survey, List<CampusBuilding> buildings) {
        this.survey = survey;
        this.buildings = List.copyOf(buildings);
        for (SurveyGraph.Vertex v : survey.vertices()) {
            vertexFloors.put(v.id(), v.floor());
            if (v.code() != null) {
                vertexCodes.put(v.id(), v.code());
            }
            if (v.type() == 1 && v.label() != null) {
                entranceLabels.put(v.id(), v.label());
            }
            if (v.code() != null && v.label() != null) {
                Matcher m = LABEL_PREFIX.matcher(v.label());
                if (m.find()) {
                    codeTokens.putIfAbsent(v.code(), m.group(1) + "동");
                }
            }
            if (v.type() == 1 && v.code() != null && v.floor() != null) {
                entrancePoints.add(new EntrancePoint(v.lat(), v.lng(), v.code(), v.floor()));
            }
        }
        this.resolveBuilding = v3BuildingResolver();
        this.base = buildBaseGraph();
    }

    // ---- 바깥에서 부르는 것 ----

    /** 건물(층)에서 건물(층)까지 후보들. 경로망이 못 덮으면 직선 어림 하나. */
    List<WalkRoute> routes(CampusBuilding from, Double fromFloor, CampusBuilding to, Double toFloor) {
        List<WalkRoute> found = findRoutes(from, fromFloor, to, toFloor, MAX_ALTERNATIVES);
        return found.isEmpty() ? List.of(straightLineFallback(from, fromFloor, to, toFloor)) : found;
    }

    /** 실측 경로망을 따라 계산한 후보들. 어느 한쪽 건물이 경로망에 없으면 빈 목록. */
    List<WalkRoute> findRoutes(CampusBuilding from, Double fromFloor, CampusBuilding to, Double toFloor, int maxAlternatives) {
        Graph graph = base.copy();
        String sourceId = attachFloorNode(graph, from, fromFloor);
        String targetId = attachFloorNode(graph, to, toFloor);
        if (!graph.nodes.containsKey(sourceId) || !graph.nodes.containsKey(targetId)) {
            return List.of();
        }
        List<String> exclude = List.of(from.name(), to.name());
        List<WalkRoute> out = new ArrayList<>();
        for (PathResult path : routeCandidates(graph, sourceId, targetId, maxAlternatives)) {
            out.add(nameVia(toRoute(graph, path), exclude));
        }
        return out;
    }

    /**
     * 내 위치에서 출발(위치를 서버로 보내지 않는 방식, 11-09). 도착 건물(층)까지 모든 실외 길 점에서의 가장 빠른 길을 한 번에 낸다 —
     * 앱이 내 위치에서 가까운 길 점(150m 안 3곳)까지 걷는 시간을 더해 가장 빠른 것을 고른다. 예전 앱 계산(내 위치를 그 3곳에 잇고
     * 최단 경로)과 같은 길이 나온다. 경로망이 도착 건물을 못 덮으면 빈 결과(앱이 직선 어림).
     */
    Approach approach(CampusBuilding to, Double toFloor) {
        Graph graph = base.copy();
        String targetId = attachFloorNode(graph, to, toFloor);
        if (!graph.nodes.containsKey(targetId)) {
            return new Approach(List.of(), List.of());
        }
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> next = new HashMap<>();
        shortestTree(graph, targetId, dist, next);
        List<String> exclude = List.of(to.name());
        Map<String, ApproachNode> nodes = new LinkedHashMap<>();
        List<ApproachStart> starts = new ArrayList<>();
        for (String id : graph.nodes.keySet()) {
            if (!isOutdoorPathPoint(graph, id) || !dist.containsKey(id)) {
                continue;
            }
            List<String> path = new ArrayList<>();
            for (String cursor = id; cursor != null; cursor = next.get(cursor)) {
                path.add(cursor);
                if (!nodes.containsKey(cursor)) {
                    Point p = graph.nodes.get(cursor);
                    nodes.put(cursor, new ApproachNode(cursor, p.lat(), p.lng(), next.get(cursor)));
                }
            }
            WalkRoute route = nameVia(toRoute(graph, new PathResult(List.copyOf(path), dist.get(id))), exclude);
            starts.add(new ApproachStart(id, withoutPoints(route)));
        }
        return new Approach(List.copyOf(nodes.values()), List.copyOf(starts));
    }

    /** 내 위치 출발용: 길 점들(다음 점 id, 도착이면 null)과 각 실외 길 점에서 도착까지의 길(점 목록은 nodes 를 따라 만든다). */
    public record Approach(List<ApproachNode> nodes, List<ApproachStart> starts) {
    }

    public record ApproachNode(String id, double lat, double lng, String next) {
    }

    public record ApproachStart(String node, WalkRoute route) {
    }

    private static WalkRoute withoutPoints(WalkRoute r) {
        return new WalkRoute(null, r.distanceMeters(), r.totalSeconds(), r.minutes(), r.hasIndoorTransit(), r.hasStairs(),
                r.hasLinkSegment(), r.isEstimate(), r.usesElevator(), r.indoorMeters(), r.viaBuildings(), r.notes(), r.steps(),
                r.indoorSegments());
    }

    /** 출입구 간 직선거리 + 층 이동 시간 어림. */
    WalkRoute straightLineFallback(CampusBuilding fromBuilding, Double fromFloor, CampusBuilding toBuilding, Double toFloor) {
        Point fromPoint = Floors.resolveEntrancePoint(fromBuilding, fromFloor);
        Point toPoint = Floors.resolveEntrancePoint(toBuilding, toFloor);
        double meters = Geo.haversineMeters(fromPoint.lat(), fromPoint.lng(), toPoint.lat(), toPoint.lng());
        double walkSeconds = (meters / WALKING_METERS_PER_MINUTE) * 60;
        double totalSeconds = walkSeconds + Floors.entranceTransitSeconds(fromBuilding, fromFloor)
                + Floors.entranceTransitSeconds(toBuilding, toFloor);
        return new WalkRoute(List.of(fromPoint, toPoint), Math.round(meters), totalSeconds, Math.max(1, Math.round(totalSeconds / 60)),
                fromFloor != null || toFloor != null, false, false, true, null, null, null, null, null, null);
    }

    // ---- 이름표·메모 ----

    /** 출입구·엘리베이터 이름표(HI_T_3F_ENTER)를 사람이 읽는 말로. */
    static String readableCode(String code) {
        Matcher entrance = ENTRANCE_CODE.matcher(code);
        if (entrance.find()) {
            return entrance.group(1) + "동 " + (entrance.group(2).isEmpty() ? "" : "지하 ") + entrance.group(3) + "층 출입구";
        }
        Matcher elevator = ELEVATOR_CODE.matcher(code);
        if (elevator.find()) {
            return elevator.group(1) + "동 엘리베이터";
        }
        return code;
    }

    private static final Pattern MEMO_LINE_SPLIT = Pattern.compile("\\n+");
    private static final Pattern MEMO_COUNT_LINE = Pattern.compile("^\\d+대$");
    private static final Pattern MEMO_CODE_LINE = Pattern.compile("^HI_[A-Z0-9_]+(\\s*=\\s*HI_[A-Z0-9_]+)*$");
    private static final Pattern MEMO_CODE = Pattern.compile("HI_[A-Z0-9_]+");
    private static final Pattern MEMO_EQUALS = Pattern.compile("\\s*=\\s*");

    /** 조사 메모를 길 안내 한 줄로. 조사 경로 이름("상수역 -> T동")·이름표만 있는 줄·'2대' 같은 줄은 버린다. 쓸 게 없으면 null. */
    static String readableMemo(String memo) {
        if (memo.contains("->")) {
            return null;
        }
        List<String> kept = new ArrayList<>();
        for (String raw : MEMO_LINE_SPLIT.split(memo, -1)) {
            String line = raw.strip();
            if (line.isEmpty() || MEMO_COUNT_LINE.matcher(line).find()) {
                continue;
            }
            if (MEMO_CODE_LINE.matcher(line).find()) {
                if (line.contains("_ELEVATOR")) {
                    kept.add(readableCode(MEMO_EQUALS.split(line, -1)[0]));
                }
                continue;
            }
            kept.add(MEMO_CODE.matcher(line).replaceAll(m -> Matcher.quoteReplacement(readableCode(m.group()))));
        }
        String text = String.join(" · ", kept).strip();
        return text.isEmpty() ? null : text;
    }

    /** 실외 길 위의 점인지(실내 `@`, 층 합성 `::`, 출입구 앵커 `#`, 내 위치가 아님). */
    private static boolean isOutdoorId(String id) {
        return id != null && !id.contains("@") && !id.contains("::") && !id.contains("#") && !id.equals(MY_LOCATION_NODE);
    }

    static String floorText(double floor) {
        return floor < 0 ? "지하 " + Floors.num(-floor) + "층" : floor == 0 ? "L층" : Floors.num(floor) + "층";
    }

    private String codeToken(String code) {
        return code == null ? null : codeTokens.get(code);
    }

    /** 실외 계단의 오르내림: 계단 시작·끝 근처(35m) 같은 건물 출입구의 층을 비교한다. 엇갈리거나 모르면 0. */
    private int stairRiseLocal(Point from, Point to) {
        Map<String, double[]> a = nearestEntrances(from);
        Map<String, double[]> b = nearestEntrances(to);
        int sign = 0;
        for (Map.Entry<String, double[]> e : a.entrySet()) {
            double[] y = b.get(e.getKey());
            double xFloor = e.getValue()[0];
            if (y == null || y[0] == xFloor) {
                continue;
            }
            int next = (int) Math.signum(y[0] - xFloor);
            if (sign != 0 && next != sign) {
                return 0;
            }
            sign = next;
        }
        return sign;
    }

    /** 건물 code → [층, 거리] (35m 안에서 가장 가까운 출입구). */
    private Map<String, double[]> nearestEntrances(Point at) {
        Map<String, double[]> byCode = new LinkedHashMap<>();
        for (EntrancePoint e : entrancePoints) {
            double meters = Geo.haversineMeters(at.lat(), at.lng(), e.lat(), e.lng());
            if (meters > STAIR_ENTRANCE_METERS) {
                continue;
            }
            double[] prev = byCode.get(e.code());
            if (prev == null || meters < prev[1]) {
                byCode.put(e.code(), new double[]{e.floor(), meters});
            }
        }
        return byCode;
    }

    private static final Pattern MEMO_BUILDING_TOKEN = Pattern.compile("[A-Z][0-9]?동");

    /** 메모로 계단 방향 어림: 'L동 4층 올라가는 계단'은 L동 쪽이 위다. 맞지 않으면 0. */
    private static int memoRise(String memo, Set<String> before, Set<String> after) {
        boolean up = memo.contains("올라가");
        boolean down = memo.contains("내려가");
        if (up == down) {
            return 0;
        }
        Matcher m = MEMO_BUILDING_TOKEN.matcher(memo);
        while (m.find()) {
            String token = m.group();
            if (after.contains(token) && !before.contains(token)) {
                return up ? 1 : -1;
            }
            if (before.contains(token) && !after.contains(token)) {
                return up ? -1 : 1;
            }
        }
        return 0;
    }

    private static String surveyNodeId(String id) {
        int at = id.indexOf('@');
        return at < 0 ? id : id.substring(0, at);
    }

    private String edgeMemo(String a, String b) {
        String x = surveyNodeId(a);
        String y = surveyNodeId(b);
        return survey.edgeMemos().get(x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x);
    }

    private record Step(String text, String kind, String building, Double floor) {
    }

    private static final Pattern ENTRANCE_NAME_TAIL = Pattern.compile(" (지하 \\d+층|L층|[\\d.]+층) 출입구$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static Double floorNodeFloor(String id) {
        if (id == null) {
            return null;
        }
        Matcher m = FLOOR_NODE.matcher(id);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }

    /** 길 흐름: 건물을 나가고 들어가는 출입구, 건물 안 층 이동, 실외 계단을 순서대로. 중간 건물을 바로 지나가면 '○동 2층 → 3층 통과'. */
    private List<String> routeSteps(Graph graph, List<String> nodeIds, Function<String, String> nameOf) {
        int n = nodeIds.size();
        java.util.function.IntFunction<GraphEdge> edgeAt = i -> i < 0 || i >= n - 1 ? null : graph.edge(nodeIds.get(i), nodeIds.get(i + 1));
        Function<String, String> entranceName = id -> {
            Double floor = vertexFloors.get(id);
            String code = vertexCodes.get(id);
            String known = code != null ? nameOf.apply(code) : null;
            return known != null && !known.isEmpty() && floor != null
                    ? known + " " + floorText(floor) + " 출입구"
                    : readableCode(entranceLabels.getOrDefault(id, id));
        };
        List<String> tokenAt = new ArrayList<>(n);
        for (String id : nodeIds) {
            tokenAt.add(codeToken(vertexCodes.get(id)));
        }

        List<Step> steps = new ArrayList<>();
        Double lastFloor = null;
        Double startFloor = n > 0 ? floorNodeFloor(nodeIds.get(0)) : null;
        for (int i = 0; i < n; i++) {
            String id = nodeIds.get(i);
            Double f = vertexFloors.get(id);
            if (startFloor != null && f != null) {
                if (!f.equals(startFloor)) {
                    steps.add(new Step("계단·엘리베이터로 " + floorText(f) + " " + (f > startFloor ? "올라가기" : "내려가기"), "move", null, null));
                }
                startFloor = null;
            }
            if (entranceLabels.containsKey(id)) {
                String name = entranceName.apply(id);
                String building = ENTRANCE_NAME_TAIL.matcher(name).replaceFirst("");
                Double floor = vertexFloors.get(id);
                boolean before = isOutdoorId(i > 0 ? nodeIds.get(i - 1) : null);
                boolean after = isOutdoorId(i + 1 < n ? nodeIds.get(i + 1) : null);
                if (before && !after) {
                    steps.add(new Step(name, "enter", building, floor));
                } else if (after && !before) {
                    steps.add(new Step(name.replaceFirst("출입구$", "출구"), "exit", building, floor));
                }
            }
            if (f != null) {
                lastFloor = f;
            }
            GraphEdge edge = edgeAt.apply(i);
            if (edge == null) {
                continue;
            }
            if (edge.vertical() && edge.seconds() > 0) {
                Double from = vertexFloors.get(id) != null ? vertexFloors.get(id) : lastFloor;
                int j = i;
                boolean stairs = false;
                for (GraphEdge e = edgeAt.apply(j); e != null && e.vertical() && e.seconds() > 0; e = edgeAt.apply(j)) {
                    if (e.stairs()) {
                        stairs = true;
                    }
                    j++;
                }
                Double to = j < n ? vertexFloors.get(nodeIds.get(j)) : null;
                if (from != null && to != null && !to.equals(from)) {
                    steps.add(new Step((stairs ? "계단으로" : "엘리베이터로") + " " + floorText(to) + " " + (to > from ? "올라가기" : "내려가기"),
                            "move", null, null));
                }
                i = j - 1;
                continue;
            }
            if (edge.stairs() && !edge.vertical() && isOutdoorId(id)) {
                int j = i;
                for (GraphEdge e = edgeAt.apply(j); e != null && e.stairs() && !e.vertical(); e = edgeAt.apply(j)) {
                    j++;
                }
                Point fromPoint = graph.nodes.get(nodeIds.get(i));
                Point toPoint = j < n ? graph.nodes.get(nodeIds.get(j)) : null;
                int rise = fromPoint != null && toPoint != null ? stairRiseLocal(fromPoint, toPoint) : 0;
                if (rise == 0) {
                    Set<String> before = new HashSet<>();
                    for (String t : tokenAt.subList(0, i)) {
                        if (t != null && !t.isEmpty()) {
                            before.add(t);
                        }
                    }
                    Set<String> after = new HashSet<>();
                    for (String t : tokenAt.subList(Math.min(n, j + 1), n)) {
                        if (t != null && !t.isEmpty()) {
                            after.add(t);
                        }
                    }
                    for (int k = Math.max(0, i - 1); k <= Math.min(n - 1, j + 1) && rise == 0; k++) {
                        String memo = survey.nodeMemos().get(surveyNodeId(nodeIds.get(k)));
                        if (memo != null && !memo.isEmpty()) {
                            rise = memoRise(memo, before, after);
                        }
                        String next = k + 1 < n ? nodeIds.get(k + 1) : null;
                        String em = next != null ? edgeMemo(nodeIds.get(k), next) : null;
                        if (rise == 0 && em != null && !em.isEmpty()) {
                            rise = memoRise(em, before, after);
                        }
                    }
                }
                steps.add(new Step(rise > 0 ? "계단 올라가기" : rise < 0 ? "계단 내려가기" : "계단 이용", "move", null, null));
                i = j - 1;
            }
        }

        Double targetFloor = n > 0 ? floorNodeFloor(nodeIds.get(n - 1)) : null;
        if (targetFloor != null && lastFloor != null && !targetFloor.equals(lastFloor)) {
            steps.add(new Step("계단·엘리베이터로 " + floorText(targetFloor) + " " + (targetFloor > lastFloor ? "올라가기" : "내려가기"),
                    "move", null, null));
        }

        List<String> out = new ArrayList<>();
        for (int k = 0; k < steps.size(); k++) {
            Step s = steps.get(k);
            Step next = k + 1 < steps.size() ? steps.get(k + 1) : null;
            if (s.kind().equals("enter") && next != null && next.kind().equals("exit") && Objects.equals(next.building(), s.building())) {
                String inFloor = s.floor() == null ? "" : floorText(s.floor());
                String outFloor = next.floor() == null ? "" : floorText(next.floor());
                String text = s.building() + " " + inFloor + (!outFloor.isEmpty() && !outFloor.equals(inFloor) ? " → " + outFloor : "") + " 통과";
                out.add(WHITESPACE.matcher(text).replaceAll(" "));
                k++;
                continue;
            }
            if (out.isEmpty() || !out.get(out.size() - 1).equals(s.text())) {
                out.add(s.text());
            }
        }
        return out;
    }

    // ---- 그래프 ----

    private static Set<String> largestComponent(Graph graph) {
        Set<String> seen = new HashSet<>();
        Set<String> best = new HashSet<>();
        for (String start : graph.nodes.keySet()) {
            if (seen.contains(start)) {
                continue;
            }
            Set<String> component = new HashSet<>();
            component.add(start);
            seen.add(start);
            ArrayList<String> stack = new ArrayList<>();
            stack.add(start);
            while (!stack.isEmpty()) {
                String id = stack.remove(stack.size() - 1);
                for (GraphEdge edge : graph.edges(id)) {
                    if (seen.contains(edge.to())) {
                        continue;
                    }
                    seen.add(edge.to());
                    component.add(edge.to());
                    stack.add(edge.to());
                }
            }
            if (component.size() > best.size()) {
                best = component;
            }
        }
        return best;
    }

    private static void addEdge(Graph graph, String aId, Point a, String bId, Point b, boolean stairs) {
        graph.nodes.put(aId, a);
        graph.nodes.put(bId, b);
        double meters = Geo.haversineMeters(a.lat(), a.lng(), b.lat(), b.lng());
        double seconds = (meters / WALKING_METERS_PER_MINUTE) * 60 * (stairs ? STAIRS_TIME_FACTOR : 1);
        graph.push(aId, new GraphEdge(bId, seconds, meters, stairs, false, false));
        graph.push(bId, new GraphEdge(aId, seconds, meters, stairs, false, false));
    }

    /** 출입구 이름의 건물 코드(HI_R_… 의 'R') → 건물. */
    private Map<String, CampusBuilding> buildingsByLabelCode() {
        Map<String, CampusBuilding> byCode = new HashMap<>();
        for (CampusBuilding building : buildings) {
            for (CampusBuilding.Entrance entrance : building.entrances()) {
                Matcher m = LABEL_PREFIX.matcher(entrance.label());
                if (m.find()) {
                    byCode.putIfAbsent(m.group(1), building);
                }
            }
        }
        return byCode;
    }

    /** 경로망 조사 v3 의 건물 code(`hongik_r`) → 건물. 서버 건물 code 로, 없으면 v3 출입구 이름 앞부분을 서버 출입구 이름과 맞춰 찾는다. */
    private Function<String, CampusBuilding> v3BuildingResolver() {
        Map<String, CampusBuilding> byServerCode = new HashMap<>();
        for (CampusBuilding building : buildings) {
            if (building.code() != null && !building.code().isEmpty()) {
                byServerCode.put(building.code(), building);
            }
        }
        Map<String, CampusBuilding> byLabelCode = buildingsByLabelCode();
        Map<String, CampusBuilding> viaLabel = new HashMap<>();
        for (SurveyGraph.Vertex v : survey.vertices()) {
            if (v.code() == null || v.code().isEmpty() || v.label() == null || v.label().isEmpty() || viaLabel.containsKey(v.code())) {
                continue;
            }
            Matcher m = LABEL_PREFIX.matcher(v.label());
            CampusBuilding building = m.find() ? byLabelCode.get(m.group(1)) : null;
            if (building != null) {
                viaLabel.put(v.code(), building);
            }
        }
        return code -> byServerCode.getOrDefault(code, viaLabel.get(code));
    }

    private static double verticalSeconds(String kind, double fromFloor, double toFloor) {
        double floors = Math.abs(Floors.levelIndex(fromFloor) - Floors.levelIndex(toFloor));
        return kind.equals("stairs_vertical")
                ? floors * FLOOR_TRANSIT_SECONDS
                : ELEVATOR_WAIT_SECONDS + floors * ELEVATOR_SECONDS_PER_FLOOR;
    }

    /** 경로망 조사 v3 그대로: 실외 길, 건물 안 층마다의 정점, 계단·엘리베이터 수직 간선, 같은 높이 층을 잇는 tie. */
    private Graph buildBaseGraph() {
        Graph graph = new Graph();
        Map<String, SurveyGraph.Vertex> vertexById = new HashMap<>();
        for (SurveyGraph.Vertex v : survey.vertices()) {
            vertexById.put(v.id(), v);
        }
        for (SurveyGraph.Edge e : survey.edges()) {
            SurveyGraph.Vertex a = vertexById.get(e.a());
            SurveyGraph.Vertex b = vertexById.get(e.b());
            if (a == null || b == null) {
                continue;
            }
            Point aPoint = new Point(a.lat(), a.lng());
            Point bPoint = new Point(b.lat(), b.lng());
            String kind = e.kind();
            if (kind.equals("normal") || kind.equals("stairs")) {
                addEdge(graph, a.id(), aPoint, b.id(), bPoint, kind.equals("stairs"));
                continue;
            }
            graph.nodes.put(a.id(), aPoint);
            graph.nodes.put(b.id(), bPoint);
            double seconds = 0;
            if (kind.equals("stairs_vertical") || kind.equals("elevator_vertical")) {
                if (a.floor() == null || b.floor() == null) {
                    continue;
                }
                seconds = verticalSeconds(kind, a.floor(), b.floor());
            }
            boolean stairs = kind.equals("stairs_vertical");
            boolean vertical = !kind.equals("tie");
            graph.push(a.id(), new GraphEdge(b.id(), seconds, 0, stairs, false, vertical));
            graph.push(b.id(), new GraphEdge(a.id(), seconds, 0, stairs, false, vertical));
        }

        // 출입구가 길 한가운데 찍혀 앞뒤 실외 점이 그 출입구로만 이어진 곳은 앞뒤 점을 바로 잇는다.
        for (SurveyGraph.Vertex v : survey.vertices()) {
            if (v.type() != 1) {
                continue;
            }
            List<String> outside = new ArrayList<>();
            for (GraphEdge e : graph.edges(v.id())) {
                if (!e.to().contains("@") && !e.vertical() && !e.stairs()) {
                    outside.add(e.to());
                }
            }
            for (int i = 0; i < outside.size(); i++) {
                for (int j = i + 1; j < outside.size(); j++) {
                    Point a = graph.nodes.get(outside.get(i));
                    Point b = graph.nodes.get(outside.get(j));
                    if (a != null && b != null && graph.edge(outside.get(i), outside.get(j)) == null) {
                        addEdge(graph, outside.get(i), a, outside.get(j), b, false);
                    }
                }
            }
        }

        graph.mainComponent = largestComponent(graph);
        for (SurveyGraph.Vertex v : survey.vertices()) {
            if (v.code() == null || v.code().isEmpty() || v.floor() == null || !graph.mainComponent.contains(v.id())) {
                continue;
            }
            CampusBuilding building = resolveBuilding.apply(v.code());
            if (building == null) {
                continue;
            }
            graph.levelVertices.computeIfAbsent(building.name(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(v.floor(), k -> new ArrayList<>()).add(v.id());
            if (v.type() != 1) {
                continue;
            }
            graph.entrancesByBuilding.computeIfAbsent(building.name(), k -> new ArrayList<>()).add(new FloorEntrance(v.id(), v.floor()));
        }
        return graph;
    }

    private static boolean isOutdoorPathPoint(Graph graph, String id) {
        return graph.mainComponent.contains(id) && !graph.floorNodeIds.contains(id) && !id.contains("@") && !id.contains("#")
                && !id.contains("::") && !id.equals(MY_LOCATION_NODE);
    }

    /**
     * 고른 건물·층에서 출발(도착)하는 합성 노드(건물 가운데)를 붙인다. 그 층에 조사된 실내 정점과는 걷는 시간으로,
     * 그 밖의 출입구와는 층 차이만큼 시간을 더해 잇는다. 건물이 경로망에 없으면 그래프에 없는 id 를 돌려준다.
     */
    private String attachFloorNode(Graph graph, CampusBuilding building, Double floor) {
        List<FloorEntrance> entrances = graph.entrancesByBuilding.containsKey(building.name())
                ? graph.entrancesByBuilding.get(building.name())
                : linkToNearestPaths(graph, building);
        List<String> surveyed = List.of();
        if (floor != null) {
            Map<Double, List<String>> byFloor = graph.levelVertices.get(building.name());
            if (byFloor != null && byFloor.get(floor) != null) {
                surveyed = byFloor.get(floor);
            }
        }
        String floorNodeId = building.name() + "::" + (floor == null ? "any" : Floors.num(floor));
        String first = !surveyed.isEmpty() ? surveyed.get(0) : !entrances.isEmpty() ? entrances.get(0).id() : null;
        if (first == null) {
            return floorNodeId;
        }
        Point center = new Point(building.lat(), building.lng());
        graph.nodes.put(floorNodeId, center);
        graph.floorNodeIds.add(floorNodeId);
        for (String id : surveyed) {
            linkCenter(graph, floorNodeId, center, id, 0);
        }
        for (FloorEntrance entrance : entrances) {
            if (surveyed.contains(entrance.id())) {
                continue;
            }
            linkCenter(graph, floorNodeId, center, entrance.id(),
                    floor == null ? 0 : Math.abs(Floors.levelIndex(floor) - Floors.levelIndex(entrance.floor())) * FLOOR_TRANSIT_SECONDS);
        }
        return floorNodeId;
    }

    private static void linkCenter(Graph graph, String floorNodeId, Point center, String id, double extraSeconds) {
        Point point = graph.nodes.get(id);
        double meters = point != null ? Geo.haversineMeters(center.lat(), center.lng(), point.lat(), point.lng()) : 0;
        double seconds = (meters / WALKING_METERS_PER_MINUTE) * 60 + extraSeconds;
        graph.push(floorNodeId, new GraphEdge(id, seconds, meters, false, false, false));
        graph.push(id, new GraphEdge(floorNodeId, seconds, meters, false, false, false));
    }

    /** 경로망에 출입구가 없는 건물은 출입구(없으면 건물 중심)에서 가장 가까운 길 점(300m 안, 2곳)까지 직선으로 잇는다. */
    private static List<FloorEntrance> linkToNearestPaths(Graph graph, CampusBuilding building) {
        record PathPoint(String id, Point point, double meters) {
        }
        record Start(String id, Point point, double floor) {
        }
        List<Map.Entry<String, Point>> pathPoints = new ArrayList<>();
        for (Map.Entry<String, Point> e : graph.nodes.entrySet()) {
            if (isOutdoorPathPoint(graph, e.getKey())) {
                pathPoints.add(Map.entry(e.getKey(), e.getValue()));
            }
        }
        List<Start> starts = new ArrayList<>();
        if (!building.entrances().isEmpty()) {
            for (CampusBuilding.Entrance e : building.entrances()) {
                starts.add(new Start(building.name() + "#" + e.label(), new Point(e.lat(), e.lng()), e.minFloor()));
            }
        } else {
            starts.add(new Start(building.name() + "#center", new Point(building.lat(), building.lng()), 1));
        }
        List<FloorEntrance> linked = new ArrayList<>();
        for (Start start : starts) {
            List<PathPoint> near = new ArrayList<>();
            for (Map.Entry<String, Point> p : pathPoints) {
                double meters = Geo.haversineMeters(start.point().lat(), start.point().lng(), p.getValue().lat(), p.getValue().lng());
                if (meters <= LINK_MAX_METERS) {
                    near.add(new PathPoint(p.getKey(), p.getValue(), meters));
                }
            }
            near.sort(Comparator.comparingDouble(PathPoint::meters));
            if (near.size() > LINK_POINTS) {
                near = near.subList(0, LINK_POINTS);
            }
            if (near.isEmpty()) {
                continue;
            }
            graph.nodes.put(start.id(), start.point());
            for (PathPoint p : near) {
                double seconds = (p.meters() / WALKING_METERS_PER_MINUTE) * 60;
                graph.push(start.id(), new GraphEdge(p.id(), seconds, p.meters(), false, true, false));
                graph.push(p.id(), new GraphEdge(start.id(), seconds, p.meters(), false, true, false));
            }
            linked.add(new FloorEntrance(start.id(), start.floor()));
        }
        return linked;
    }

    // ---- 탐색 ----

    @FunctionalInterface
    private interface EdgeWeight {
        double cost(String from, GraphEdge edge);
    }

    private record QueueEntry(double dist, long seq, String id) {
    }

    /**
     * 다익스트라. 앱은 방문하지 않은 점 중 거리가 가장 작은 점을 '처음 거리를 얻은 순서'로 훑어 같은 거리면 먼저 얻은 점을 골랐다 —
     * 우선순위 큐에 (거리, 처음 얻은 순서)로 넣어 같은 점을 같은 순서로 고른다.
     */
    private static PathResult dijkstra(Graph graph, String sourceId, String targetId, Set<String> excludedNodes, Set<String> excludedEdges,
                                       EdgeWeight weight) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, Long> order = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::dist).thenComparingLong(QueueEntry::seq));
        long[] nextSeq = {0};
        dist.put(sourceId, 0.0);
        order.put(sourceId, nextSeq[0]++);
        queue.add(new QueueEntry(0, 0, sourceId));

        while (!queue.isEmpty()) {
            QueueEntry current = queue.poll();
            String currentId = current.id();
            if (visited.contains(currentId) || current.dist() != dist.get(currentId)) {
                continue;
            }
            if (currentId.equals(targetId)) {
                break;
            }
            visited.add(currentId);
            double currentDist = current.dist();
            for (GraphEdge edge : graph.edges(currentId)) {
                if (excludedNodes.contains(edge.to()) || excludedEdges.contains(currentId + "->" + edge.to())) {
                    continue;
                }
                double cost = weight != null ? weight.cost(currentId, edge) : edge.seconds();
                if (cost == Double.POSITIVE_INFINITY) {
                    continue;
                }
                double next = currentDist + cost;
                Double known = dist.get(edge.to());
                if (known == null || next < known) {
                    dist.put(edge.to(), next);
                    Long seq = order.get(edge.to());
                    if (seq == null) {
                        seq = nextSeq[0]++;
                        order.put(edge.to(), seq);
                    }
                    prev.put(edge.to(), currentId);
                    queue.add(new QueueEntry(next, seq, edge.to()));
                }
            }
        }

        if (!dist.containsKey(targetId)) {
            return null;
        }
        List<String> nodeIds = new ArrayList<>();
        nodeIds.add(targetId);
        for (String cursor = targetId; !cursor.equals(sourceId); ) {
            String p = prev.get(cursor);
            if (p == null) {
                return null;
            }
            nodeIds.add(p);
            cursor = p;
        }
        java.util.Collections.reverse(nodeIds);
        return new PathResult(List.copyOf(nodeIds), dist.get(targetId));
    }

    /**
     * 한 점(도착)에서 모든 점까지의 가장 빠른 시간과, 각 점에서 도착 쪽으로 가는 다음 점. 간선은 양방향·같은 시간이라
     * 도착에서 거꾸로 잰 값이 그 점에서 도착까지의 시간이다. 같은 시간이면 dijkstra 와 같은 순서로 고른다.
     */
    private static void shortestTree(Graph graph, String rootId, Map<String, Double> dist, Map<String, String> next) {
        Map<String, Long> order = new HashMap<>();
        Set<String> visited = new HashSet<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::dist).thenComparingLong(QueueEntry::seq));
        long seq = 0;
        dist.put(rootId, 0.0);
        order.put(rootId, seq++);
        queue.add(new QueueEntry(0, 0, rootId));
        while (!queue.isEmpty()) {
            QueueEntry current = queue.poll();
            if (visited.contains(current.id()) || current.dist() != dist.get(current.id())) {
                continue;
            }
            visited.add(current.id());
            for (GraphEdge edge : graph.edges(current.id())) {
                double candidate = current.dist() + edge.seconds();
                Double known = dist.get(edge.to());
                if (known == null || candidate < known) {
                    dist.put(edge.to(), candidate);
                    Long s = order.get(edge.to());
                    if (s == null) {
                        s = seq++;
                        order.put(edge.to(), s);
                    }
                    next.put(edge.to(), current.id());
                    queue.add(new QueueEntry(candidate, s, edge.to()));
                }
            }
        }
    }

    private static double pathSeconds(Graph graph, List<String> nodeIds) {
        double total = 0;
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            GraphEdge edge = graph.edge(nodeIds.get(i), nodeIds.get(i + 1));
            if (edge != null) {
                total += edge.seconds();
            }
        }
        return total;
    }

    private static String pathKey(List<String> nodeIds) {
        return String.join(">", nodeIds);
    }

    /** Yen's algorithm — 루프 없는 k 개의 최단 경로 후보. */
    private static List<PathResult> yenKShortestPaths(Graph graph, String sourceId, String targetId, int k) {
        PathResult first = dijkstra(graph, sourceId, targetId, Set.of(), Set.of(), null);
        if (first == null) {
            return List.of();
        }
        List<PathResult> a = new ArrayList<>();
        a.add(first);
        Set<String> seen = new HashSet<>();
        seen.add(pathKey(first.nodeIds()));
        List<PathResult> b = new ArrayList<>();

        for (int i = 1; i < k; i++) {
            List<String> prevPath = a.get(i - 1).nodeIds();
            for (int spurIndex = 0; spurIndex < prevPath.size() - 1; spurIndex++) {
                String spurNode = prevPath.get(spurIndex);
                List<String> rootPath = prevPath.subList(0, spurIndex + 1);
                Set<String> excludedEdges = new HashSet<>();
                for (PathResult path : a) {
                    if (path.nodeIds().size() > spurIndex && path.nodeIds().subList(0, spurIndex + 1).equals(rootPath)
                            && spurIndex + 1 < path.nodeIds().size()) {
                        excludedEdges.add(path.nodeIds().get(spurIndex) + "->" + path.nodeIds().get(spurIndex + 1));
                    }
                }
                Set<String> excludedNodes = new HashSet<>(rootPath.subList(0, rootPath.size() - 1));
                PathResult spur = dijkstra(graph, spurNode, targetId, excludedNodes, excludedEdges, null);
                if (spur == null) {
                    continue;
                }
                List<String> total = new ArrayList<>(rootPath.subList(0, rootPath.size() - 1));
                total.addAll(spur.nodeIds());
                String key = pathKey(total);
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);
                b.add(new PathResult(List.copyOf(total), pathSeconds(graph, rootPath) + spur.seconds()));
            }
            if (b.isEmpty()) {
                break;
            }
            b.sort(Comparator.comparingDouble(PathResult::seconds));
            a.add(b.remove(0));
        }
        return a;
    }

    /** 경로의 간선(방향 무시) → 길이(m). */
    private static Map<String, Double> edgeMeters(Graph graph, List<String> nodeIds) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            String a = nodeIds.get(i);
            String b = nodeIds.get(i + 1);
            GraphEdge edge = graph.edge(a, b);
            map.put(a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a, edge != null ? edge.meters() : 0);
        }
        return map;
    }

    private static double totalMeters(Map<String, Double> edges) {
        double sum = 0;
        for (double m : edges.values()) {
            sum += m;
        }
        return sum;
    }

    /** 두 길이 얼마나 겹치는지 — 길이(m)로 잰다. */
    private static double overlapRatio(Map<String, Double> a, Map<String, Double> b) {
        double ta = totalMeters(a);
        double tb = totalMeters(b);
        if (ta == 0 || tb == 0) {
            return 0;
        }
        double shared = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            if (b.containsKey(e.getKey())) {
                shared += e.getValue();
            }
        }
        return shared / Math.min(ta, tb);
    }

    /** 후보 중 서로 많이 겹치지 않는 것만(60% 미만) 고른다. */
    private static List<PathResult> pickDiverseRoutes(Graph graph, List<PathResult> candidates, int maxResults) {
        List<PathResult> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(PathResult::seconds));
        List<PathResult> picked = new ArrayList<>();
        List<Map<String, Double>> pickedEdges = new ArrayList<>();
        for (PathResult candidate : sorted) {
            if (picked.size() >= maxResults) {
                break;
            }
            Map<String, Double> edges = edgeMeters(graph, candidate.nodeIds());
            boolean overlaps = false;
            for (Map<String, Double> existing : pickedEdges) {
                if (overlapRatio(edges, existing) >= 0.6) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) {
                continue;
            }
            picked.add(candidate);
            pickedEdges.add(edges);
        }
        return picked;
    }

    /** 가장 빠른 길의 1.25배 또는 +1분 중 큰 쪽까지. */
    private static boolean withinDetour(double seconds, double bestSeconds) {
        return seconds <= Math.max(bestSeconds * 1.25, bestSeconds + 60);
    }

    /** 억지 우회: 다른 건물 출입구를 찍고 되돌아 나오거나, 이미 지난 자리(8m 안)로 30m 넘게 돌아 다시 오는 길. */
    private boolean isContrivedDetour(Graph graph, List<String> nodeIds) {
        for (int i = 1; i < nodeIds.size() - 1; i++) {
            if (entranceLabels.containsKey(nodeIds.get(i)) && isOutdoorId(nodeIds.get(i - 1)) && isOutdoorId(nodeIds.get(i + 1))) {
                return true;
            }
        }
        List<Point> points = new ArrayList<>();
        for (String id : nodeIds) {
            points.add(graph.nodes.get(id));
        }
        double walked = 0;
        double[] along = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            Point a = points.get(i - 1);
            Point b = points.get(i);
            walked += a != null && b != null ? Geo.haversineMeters(a.lat(), a.lng(), b.lat(), b.lng()) : 0;
            along[i] = walked;
        }
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 2; j < points.size(); j++) {
                Point a = points.get(i);
                Point b = points.get(j);
                if (a == null || b == null || along[j] - along[i] <= 30) {
                    continue;
                }
                if (Geo.haversineMeters(a.lat(), a.lng(), b.lat(), b.lng()) < 8) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 길찾기 후보(3개 정도): Yen 으로 가까운 순 후보를 얻고, 찾은 길의 간선을 비싸게 보고 다시 찾는 벌점 방식으로 실제로 다른 길을 더한다.
     * 가장 빠른 길이 맨 앞, 나머지는 시간 순.
     */
    private List<PathResult> routeCandidates(Graph graph, String sourceId, String targetId, int maxResults) {
        List<PathResult> yen = yenKShortestPaths(graph, sourceId, targetId, Math.max(6, maxResults * 2));
        if (yen.isEmpty()) {
            return List.of();
        }
        PathResult best = yen.get(0);
        List<PathResult> all = new ArrayList<>(yen);
        Map<String, PathResult> byKey = new HashMap<>();
        for (PathResult path : yen) {
            byKey.putIfAbsent(pathKey(path.nodeIds()), path);
        }
        Map<String, Double> penalty = new HashMap<>();
        java.util.function.Consumer<PathResult> penalize = path -> {
            for (int i = 0; i < path.nodeIds().size() - 1; i++) {
                String a = path.nodeIds().get(i);
                String b = path.nodeIds().get(i + 1);
                for (String key : new String[]{a + "->" + b, b + "->" + a}) {
                    penalty.put(key, penalty.getOrDefault(key, 1.0) * DETOUR_PENALTY);
                }
            }
        };
        penalize.accept(best);
        for (int round = 0; round < PENALTY_ROUNDS; round++) {
            PathResult raw = dijkstra(graph, sourceId, targetId, Set.of(), Set.of(),
                    (from, edge) -> edge.seconds() * penalty.getOrDefault(from + "->" + edge.to(), 1.0));
            if (raw == null) {
                break;
            }
            String key = pathKey(raw.nodeIds());
            PathResult found = byKey.get(key);
            if (found == null) {
                found = new PathResult(raw.nodeIds(), pathSeconds(graph, raw.nodeIds()));
                byKey.put(key, found);
                all.add(found);
            }
            penalize.accept(found);
        }

        List<PathResult> near = new ArrayList<>();
        for (PathResult path : all) {
            if (path == best || (withinDetour(path.seconds(), best.seconds()) && !isContrivedDetour(graph, path.nodeIds()))) {
                near.add(path);
            }
        }
        List<PathResult> picked = pickDiverseRoutes(graph, near, maxResults);
        List<PathResult> rest = new ArrayList<>(picked.subList(1, picked.size()));
        rest.sort(Comparator.comparingDouble(PathResult::seconds));
        List<PathResult> out = new ArrayList<>();
        out.add(picked.get(0));
        out.addAll(rest);
        return out;
    }

    // ---- 결과 ----

    /** 실내 정점 id(`n12@hongik_t:1`)의 건물 code. 실외·합성 노드는 null. */
    private static String indoorCode(String id) {
        int at = id.indexOf('@');
        if (at < 0) {
            return null;
        }
        int colon = id.lastIndexOf(':');
        return colon > at ? id.substring(at + 1, colon) : id.substring(at + 1);
    }

    private static final Pattern MEMO_HAS_PLACE = Pattern.compile("동|HI_|기숙사|관");

    private WalkRoute toRoute(Graph graph, PathResult path) {
        List<String> ids = path.nodeIds();
        Function<String, String> nameOf = this::shortName;
        List<Point> points = new ArrayList<>();
        for (String id : ids) {
            Point point = graph.nodes.get(id);
            Point last = points.isEmpty() ? null : points.get(points.size() - 1);
            if (point != null && !(last != null && last.lat() == point.lat() && last.lng() == point.lng())) {
                points.add(point);
            }
        }

        List<String> notes = new ArrayList<>();
        java.util.function.Consumer<String> addNote = memo -> {
            String text = memo != null ? readableMemo(memo) : null;
            if (text != null && !notes.contains(text)) {
                notes.add(text);
            }
        };
        int n = ids.size();
        java.util.function.IntFunction<GraphEdge> edgeAt = i -> i < 0 || i >= n - 1 ? null : graph.edge(ids.get(i), ids.get(i + 1));
        for (int i = 0; i < n; i++) {
            String fullId = ids.get(i);
            String id = surveyNodeId(fullId);
            String memo = survey.nodeMemos().get(id);
            GraphEdge before = edgeAt.apply(i - 1);
            GraphEdge after = edgeAt.apply(i);
            boolean stairsHere = (before != null && before.stairs()) || (after != null && after.stairs());
            if (memo != null && !memo.isEmpty() && (!memo.contains("계단") || stairsHere)) {
                String label = entranceLabels.get(fullId);
                String code = vertexCodes.get(fullId);
                Double floor = vertexFloors.get(fullId);
                String known = code != null ? nameOf.apply(code) : null;
                String where = label != null
                        ? (known != null && !known.isEmpty() && floor != null ? known + " " + floorText(floor) + " 출입구" : readableCode(label))
                        : null;
                addNote.accept(where != null && !where.isEmpty() && !MEMO_HAS_PLACE.matcher(memo).find() ? where + " " + memo : memo);
            }
            if (i + 1 < n) {
                addNote.accept(edgeMemo(id, ids.get(i + 1)));
            }
        }

        double distanceMeters = 0;
        double indoorMeters = 0;
        boolean hasIndoorTransit = false;
        boolean hasStairs = false;
        boolean hasLinkSegment = false;
        boolean usesElevator = false;
        List<String> viaCodes = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            GraphEdge edge = graph.edge(ids.get(i), ids.get(i + 1));
            if (edge == null) {
                continue;
            }
            distanceMeters += edge.meters();
            String codeA = indoorCode(ids.get(i));
            String codeB = indoorCode(edge.to());
            if (codeA != null && !codeA.isEmpty() && codeB != null && !codeB.isEmpty()) {
                indoorMeters += edge.meters();
            }
            if (edge.vertical() && !edge.stairs() && edge.seconds() > 0) {
                usesElevator = true;
            }
            for (String code : Arrays.asList(codeA, codeB)) {
                if (code != null && !code.isEmpty() && !viaCodes.contains(code)) {
                    viaCodes.add(code);
                }
            }
            if (edge.stairs()) {
                hasStairs = true;
            }
            if (edge.link()) {
                hasLinkSegment = true;
            }
            if (edge.vertical() && edge.seconds() > 0) {
                hasIndoorTransit = true;
            }
            double walkSeconds = (edge.meters() / WALKING_METERS_PER_MINUTE) * 60;
            if (edge.seconds() > walkSeconds + 0.5
                    && (graph.floorNodeIds.contains(ids.get(i)) || graph.floorNodeIds.contains(edge.to()))) {
                hasIndoorTransit = true;
            }
        }

        List<String> allNotes = new ArrayList<>(startFloorNote(ids));
        allNotes.addAll(notes);
        return new WalkRoute(List.copyOf(points), Math.round(distanceMeters), path.seconds(), Math.max(1, Math.round(path.seconds() / 60)),
                hasIndoorTransit, hasStairs, hasLinkSegment, false, usesElevator, Math.round(indoorMeters), viaCodes,
                allNotes, routeSteps(graph, ids, nameOf), indoorSegmentsOf(graph, ids));
    }

    /** 건물 안(실내 정점·건물 가운데 합성 노드 사이) 구간들. 이어진 구간은 하나로 잇는다. */
    private static List<List<Point>> indoorSegmentsOf(Graph graph, List<String> nodeIds) {
        List<List<Point>> segments = new ArrayList<>();
        List<Point> current = null;
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            String a = nodeIds.get(i);
            String b = nodeIds.get(i + 1);
            Point pa = graph.nodes.get(a);
            Point pb = graph.nodes.get(b);
            boolean inside = (a.contains("@") || graph.floorNodeIds.contains(a)) && (b.contains("@") || graph.floorNodeIds.contains(b));
            if (pa == null || pb == null || !inside || (pa.lat() == pb.lat() && pa.lng() == pb.lng())) {
                if (pa == null || pb == null || !inside) {
                    current = null;
                }
                continue;
            }
            if (current == null) {
                current = new ArrayList<>();
                current.add(pa);
                segments.add(current);
            }
            current.add(pb);
        }
        List<List<Point>> out = new ArrayList<>();
        for (List<Point> s : segments) {
            out.add(List.copyOf(s));
        }
        return out;
    }

    /** 고른 출발 층과 실제로 나가는 출구 층이 다르면 그 까닭을 한 줄로. */
    private List<String> startFloorNote(List<String> nodeIds) {
        Double chosen = nodeIds.isEmpty() ? null : floorNodeFloor(nodeIds.get(0));
        if (chosen == null) {
            return List.of();
        }
        for (int i = 1; i < nodeIds.size(); i++) {
            if (!entranceLabels.containsKey(nodeIds.get(i)) || !isOutdoorId(i + 1 < nodeIds.size() ? nodeIds.get(i + 1) : null)) {
                continue;
            }
            Double exit = vertexFloors.get(nodeIds.get(i));
            return exit != null && !exit.equals(chosen)
                    ? List.of(floorText(chosen) + " 대신 " + floorText(exit) + " 출구로 나가요(더 가까워요)")
                    : List.of();
        }
        return List.of();
    }

    private static final Pattern SHORT_NAME = Pattern.compile("^[A-Z0-9]+동$");

    /** "제4공학관 T동" → "T동". 끝이 '○동'이 아니면 이름 그대로. */
    static String shortBuildingName(String name) {
        String[] parts = WHITESPACE.split(name.strip());
        String last = parts.length > 0 ? parts[parts.length - 1] : name;
        return SHORT_NAME.matcher(last).find() ? last : name;
    }

    private String shortName(String code) {
        CampusBuilding building = resolveBuilding.apply(code);
        return building != null ? shortBuildingName(building.name()) : null;
    }

    /** viaBuildings 의 건물 code 를 이름으로 바꾸고 출발·도착 건물은 뺀다. */
    private WalkRoute nameVia(WalkRoute route, List<String> exclude) {
        List<String> names = new ArrayList<>();
        for (String code : route.viaBuildings()) {
            CampusBuilding building = resolveBuilding.apply(code);
            String name = building != null ? building.name() : null;
            if (name != null && !name.isEmpty() && !exclude.contains(name) && !names.contains(name)) {
                names.add(name);
            }
        }
        return new WalkRoute(route.points(), route.distanceMeters(), route.totalSeconds(), route.minutes(), route.hasIndoorTransit(),
                route.hasStairs(), route.hasLinkSegment(), route.isEstimate(), route.usesElevator(), route.indoorMeters(),
                List.copyOf(names), route.notes(), route.steps(), route.indoorSegments());
    }
}
