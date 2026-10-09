package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.mapdata.dto.MapPathEntrance;
import com.hongmap.hongmapbackend.mapdata.dto.MapPathNode;
import com.hongmap.hongmapbackend.mapdata.dto.MapPaths;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * 경로망을 code 기준으로 읽은 모양(DB·JPA 와 무관). 출입구 노드의 좌표 해석, 응답(paths) 조립, 연결 수·덩어리 계산을 맡는다.
 * GET /map/data, 관리자 목록·점검(path-audit), 임포트 미리보기가 같은 규칙을 쓴다.
 */
public final class PathNetwork {

    /** 출입구 노드가 가리키는 라벨이 건물 entrances 에 없다. */
    public static final String LABEL_MISSING = "LABEL_MISSING";
    /** 건물 entrances JSON 이 깨져 읽을 수 없다. */
    public static final String ENTRANCES_INVALID = "ENTRANCES_INVALID";

    /**
     * 점 하나. point 는 WAYPOINT 면 저장된 좌표, ENTRANCE 면 buildings.entrances 에서 찾은 좌표 — 못 찾으면 null 이고
     * brokenReason 이 있다.
     */
    public record Node(String code, PathNodeKind kind, String buildingCode, String entranceLabel,
                       PathGeo.Point point, String brokenReason) {

        public boolean broken() {
            return point == null;
        }
    }

    /** 간선 하나. id 는 DB 에 없는 것(임포트 미리보기)이면 null. a·b 는 노드 code. */
    public record Edge(Long id, String a, String b) {
    }

    private final Map<String, Node> nodes;   // code 순
    private final List<Edge> edges;

    public PathNetwork(Collection<Node> nodes, Collection<Edge> edges) {
        Map<String, Node> sorted = new TreeMap<>();
        for (Node n : nodes) {
            sorted.put(n.code(), n);
        }
        this.nodes = new LinkedHashMap<>(sorted);
        this.edges = List.copyOf(edges);
    }

    /** DB 엔티티로 만든다. 출입구 노드의 건물은 미리 읽어 둔다(fetch join). */
    public static PathNetwork of(List<PathNode> nodes, List<PathEdge> edges,
                                 Function<Building, BuildingEntrances.Parsed> entrances) {
        List<Node> resolved = new ArrayList<>(nodes.size());
        for (PathNode n : nodes) {
            resolved.add(resolve(n, entrances));
        }
        List<Edge> list = new ArrayList<>(edges.size());
        for (PathEdge e : edges) {
            list.add(new Edge(e.getId(), e.getNodeA().getCode(), e.getNodeB().getCode()));
        }
        return new PathNetwork(resolved, list);
    }

    static Node resolve(PathNode n, Function<Building, BuildingEntrances.Parsed> entrances) {
        if (n.getKind() == PathNodeKind.WAYPOINT) {
            return new Node(n.getCode(), PathNodeKind.WAYPOINT, null, null,
                    new PathGeo.Point(n.getLatitude().doubleValue(), n.getLongitude().doubleValue()), null);
        }
        Building b = n.getBuilding();
        BuildingEntrances.Parsed parsed = entrances.apply(b);
        PathGeo.Point point = parsed.find(n.getEntranceLabel());
        String reason = point != null ? null : parsed.invalid() ? ENTRANCES_INVALID : LABEL_MISSING;
        return new Node(n.getCode(), PathNodeKind.ENTRANCE, b.getCode(), n.getEntranceLabel(), point, reason);
    }

    public Collection<Node> nodes() {
        return nodes.values();
    }

    public List<Edge> edges() {
        return edges;
    }

    public Node node(String code) {
        return nodes.get(code);
    }

    public List<Node> brokenNodes() {
        return nodes.values().stream().filter(Node::broken).toList();
    }

    /** 간선 길이(m). 한쪽이라도 좌표가 없으면 null. */
    public Double lengthMeters(Edge e) {
        Node a = nodes.get(e.a());
        Node b = nodes.get(e.b());
        if (a == null || b == null || a.broken() || b.broken()) {
            return null;
        }
        return PathGeo.distanceMeters(a.point(), b.point());
    }

    /** code → 이어진 간선 수(0 포함). */
    public Map<String, Integer> degrees() {
        Map<String, Integer> degree = new HashMap<>();
        for (String code : nodes.keySet()) {
            degree.put(code, 0);
        }
        for (Edge e : edges) {
            degree.merge(e.a(), 1, Integer::sum);
            degree.merge(e.b(), 1, Integer::sum);
        }
        return degree;
    }

    /**
     * 이어진 덩어리들. 각 덩어리는 code 순, 목록은 큰 것부터(같으면 첫 code 순) — 맨 앞이 본망이다.
     * 간선 하나 없는 점도 크기 1 덩어리로 들어간다.
     */
    public List<List<String>> components() {
        Map<String, String> parent = new HashMap<>();
        for (String code : nodes.keySet()) {
            parent.put(code, code);
        }
        for (Edge e : edges) {
            if (parent.containsKey(e.a()) && parent.containsKey(e.b())) {
                String ra = find(parent, e.a());
                String rb = find(parent, e.b());
                if (!ra.equals(rb)) {
                    parent.put(ra, rb);
                }
            }
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String code : nodes.keySet()) {   // code 순으로 넣으므로 각 덩어리도 code 순
            groups.computeIfAbsent(find(parent, code), k -> new ArrayList<>()).add(code);
        }
        List<List<String>> result = new ArrayList<>(groups.values());
        result.sort(Comparator.<List<String>>comparingInt(List::size).reversed().thenComparing(c -> c.get(0)));
        return result;
    }

    private static String find(Map<String, String> parent, String code) {
        String root = code;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        String cur = code;
        while (!cur.equals(root)) {
            String next = parent.get(cur);
            parent.put(cur, root);
            cur = next;
        }
        return root;
    }

    /**
     * GET /map/data 의 paths. 좌표를 못 찾은 출입구 노드와 거기 이어진 간선은 뺀다(값 하나 때문에 지도가 안 뜨지 않게).
     * nodes 는 code 순, edges 는 [작은 code, 큰 code] 를 정렬 — DB id 가 바뀌어도(다시 임포트) 같은 내용이면 같은 본문이다.
     */
    public MapPaths toMapPaths() {
        List<MapPathNode> outNodes = new ArrayList<>();
        for (Node n : nodes.values()) {
            if (n.broken()) {
                continue;
            }
            MapPathEntrance entrance = n.kind() == PathNodeKind.ENTRANCE
                    ? new MapPathEntrance(n.buildingCode(), n.entranceLabel()) : null;
            outNodes.add(new MapPathNode(n.code(), n.point().lat(), n.point().lng(), entrance));
        }
        List<List<String>> outEdges = new ArrayList<>();
        for (Edge e : edges) {
            Node a = nodes.get(e.a());
            Node b = nodes.get(e.b());
            if (a == null || b == null || a.broken() || b.broken()) {
                continue;
            }
            outEdges.add(e.a().compareTo(e.b()) <= 0 ? List.of(e.a(), e.b()) : List.of(e.b(), e.a()));
        }
        outEdges.sort(Comparator.<List<String>, String>comparing(p -> p.get(0)).thenComparing(p -> p.get(1)));
        return new MapPaths(outNodes, outEdges);
    }
}
