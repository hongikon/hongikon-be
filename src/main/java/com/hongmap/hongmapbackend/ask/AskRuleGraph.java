package com.hongmap.hongmapbackend.ask;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 물어보기 규정 그래프(docs/ask-rule-graph-plan.md). 규정 사이의 관계(결과·선행조건·예외·제한·기간)를 미리 적어 둔 파일
 * (ask/rule-graph.json)에서, 고른 안내(answerIds)에 이어진 규정을 정해진 규칙으로 찾아 답 쓰기에 함께 넘긴다 —
 * 질문 낱말과 뜻이 멀어 검색으로는 올라오지 않는 이웃 규정(예: 학기재수강 → 성적경고 유지 → 제적)을 놓치지 않게.
 *
 * 선은 사람이 확인한 것(reviewed)만 읽고, 근거(guide·quote)가 지금 카탈로그와 맞지 않는 선은 버린다(4-1).
 * ask.graph.enabled=false(기본)이거나 파일이 없거나 읽기에 실패하거나 쓸 선이 없으면 꺼진다({@link #enabled()} false — 지금 동작 그대로).
 */
@Slf4j
@Component
public class AskRuleGraph {

    static final String RESOURCE = "ask/rule-graph.json";
    /** 버린 선이 이 비율을 넘으면 경고한다(카탈로그가 크게 바뀌어 그래프를 다시 검토해야 한다는 뜻). */
    static final double DROP_WARN_RATIO = 0.2;
    /** 선 종류와 자르기 순서(앞이 먼저). */
    static final List<String> TYPES = List.of("결과", "예외", "제한", "선행조건", "기간");
    /** condition 에 쓸 수 있는 칸 이름(4절). */
    static final Set<String> CONDITION_KEYS = Set.of("학년", "이수학기", "휴학중", "장학생", "분할납부", "군복무", "졸업유예");

    static final String BLOCK_HEAD = "[함께 볼 규정] 고른 안내와 이어진 규정이다. 학생 상황에 해당할 때만 결론과 결과에 반영한다.";

    private final Map<String, Node> nodes;
    private final List<Edge> edges;
    private final List<String> dropped;
    private final boolean warned;
    private final int maxEdges;
    private final int maxGuides;
    private final boolean enabled;

    @Autowired
    public AskRuleGraph(AskCatalog catalog, JsonMapper jsonMapper,
                        @Value("${ask.graph.enabled:false}") boolean wanted,
                        @Value("${ask.graph.max-edges:8}") int maxEdges,
                        @Value("${ask.graph.max-guides:2}") int maxGuides) {
        this(catalog, wanted ? read(jsonMapper, RESOURCE) : null, wanted, maxEdges, maxGuides);
    }

    AskRuleGraph(AskCatalog catalog, GraphFile file, boolean wanted, int maxEdges, int maxGuides) {
        this.maxEdges = maxEdges;
        this.maxGuides = maxGuides;
        Map<String, Node> byId = new LinkedHashMap<>();
        List<Edge> kept = new ArrayList<>();
        List<String> droppedIds = new ArrayList<>();
        int reviewed = 0;
        if (file != null) {
            for (Node node : file.nodes() == null ? List.<Node>of() : file.nodes()) {
                if (node.id() != null && !node.id().isBlank()) {
                    byId.put(node.id(), node);
                }
            }
            boolean sameCatalog = catalog.version().equals(file.catalogVersion());
            for (Edge edge : file.edges() == null ? List.<Edge>of() : file.edges()) {
                // 사람이 확인하지 않은 선은 읽지 않는다(버린 선으로 세지 않는다 — 아직 검토 중일 뿐이다).
                if (edge.reviewed() == null || edge.reviewed().isBlank()) {
                    continue;
                }
                reviewed++;
                if (usable(edge, byId, catalog, sameCatalog)) {
                    kept.add(edge);
                } else {
                    droppedIds.add(edge.id());
                }
            }
        }
        this.nodes = Map.copyOf(byId);
        this.edges = List.copyOf(kept);
        this.dropped = List.copyOf(droppedIds);
        this.warned = reviewed > 0 && droppedIds.size() > reviewed * DROP_WARN_RATIO;
        this.enabled = wanted && file != null && !kept.isEmpty();
        if (warned) {
            log.warn("물어보기 규정 그래프: 확인된 선 {}개 중 {}개를 버렸다(20% 초과 — 카탈로그가 바뀌었으면 그래프를 다시 검토한다): {}",
                    reviewed, droppedIds.size(), droppedIds);
        } else if (!droppedIds.isEmpty()) {
            log.info("물어보기 규정 그래프: 근거가 맞지 않아 버린 선 {}개: {}", droppedIds.size(), droppedIds);
        }
        if (enabled) {
            log.info("물어보기 규정 그래프 켜짐: 점 {}개, 선 {}개(버림 {}, 미확인 제외)", byId.size(), kept.size(), droppedIds.size());
        } else if (wanted) {
            log.warn("물어보기 규정 그래프를 켜려 했지만 파일({})이 없거나 쓸 선이 없어 지금 방식으로 둔다.", RESOURCE);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** 검사에서 버린 선 id(시작 로그와 테스트용). */
    List<String> dropped() {
        return dropped;
    }

    /** 버린 선이 확인된 선의 20% 를 넘었나. */
    boolean warned() {
        return warned;
    }

    /**
     * 4-1 검사. guide 가 카탈로그에 없으면 버린다. catalogVersion 이 지금 카탈로그와 다르면 quote 가 그 안내 원문에 글자 그대로 있어야 한다.
     * 이웃 찾기가 쓰는 칸(점·종류·조건 칸 이름)이 틀린 선도 버린다.
     */
    private static boolean usable(Edge edge, Map<String, Node> nodes, AskCatalog catalog, boolean sameCatalog) {
        if (!nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to()) || !TYPES.contains(edge.type())
                || edge.fact() == null || edge.fact().isBlank()) {
            return false;
        }
        if (edge.condition() != null && !CONDITION_KEYS.containsAll(edge.condition().keySet())) {
            return false;
        }
        if (edge.guide() == null || !catalog.contains(edge.guide()) || AskCatalog.NONE.equals(edge.guide())) {
            return false;
        }
        if (sameCatalog) {
            return true;
        }
        String source = catalog.source(edge.guide());
        return edge.quote() != null && !edge.quote().isBlank() && source != null && source.contains(edge.quote());
    }

    /**
     * 고른 안내에 이어진 규정(5절 2~3). 출발점은 answerIds 의 안내를 guides 에 가진 점.
     * 1단계: 출발점에서 나가는 모든 선 + 출발점으로 들어오는 예외·제한 선. 2단계: 1단계 결과 선이 닿은 점에서 나가는 결과 선만. 3단계부터는 가지 않는다.
     * 선은 단계 → 종류 순으로 최대 max-edges, 이웃 안내 원문은 1단계 결과·예외 선의 근거 안내만 최대 max-guides(answerIds 에 없는 것).
     */
    public Related related(List<String> answerIds) {
        if (!enabled || answerIds.isEmpty()) {
            return Related.EMPTY;
        }
        Set<String> starts = new LinkedHashSet<>();
        for (String id : answerIds) {
            for (Node node : nodes.values()) {
                if (node.guides() != null && node.guides().contains(id)) {
                    starts.add(node.id());
                }
            }
        }
        if (starts.isEmpty()) {
            return Related.EMPTY;
        }
        Map<Edge, Integer> found = new LinkedHashMap<>();
        Set<String> reached = new LinkedHashSet<>();
        for (Edge edge : edges) {
            boolean out = starts.contains(edge.from());
            boolean in = starts.contains(edge.to()) && (edge.type().equals("예외") || edge.type().equals("제한"));
            if (out || in) {
                found.put(edge, 1);
                if (out && edge.type().equals("결과")) {
                    reached.add(edge.to());
                }
            }
        }
        for (Edge edge : edges) {
            if (reached.contains(edge.from()) && edge.type().equals("결과")) {
                found.putIfAbsent(edge, 2);
            }
        }
        List<Edge> ordered = new ArrayList<>(found.keySet());
        // 같은 단계·종류 안에서는 파일 순서 그대로(정렬이 안정적이다).
        ordered.sort(Comparator.comparingInt((Edge edge) -> found.get(edge)).thenComparingInt(edge -> TYPES.indexOf(edge.type())));
        List<Edge> used = ordered.subList(0, Math.min(maxEdges, ordered.size()));
        List<String> guides = new ArrayList<>();
        for (Edge edge : used) {
            if (found.get(edge) == 1 && (edge.type().equals("결과") || edge.type().equals("예외"))
                    && !answerIds.contains(edge.guide()) && !guides.contains(edge.guide()) && guides.size() < maxGuides) {
                guides.add(edge.guide());
            }
        }
        StringBuilder block = new StringBuilder("\n\n").append(BLOCK_HEAD);
        for (Edge edge : used) {
            block.append('\n').append(line(edge));
        }
        return new Related(used.stream().map(Edge::id).toList(), guides, block.toString());
    }

    /** "- 학기재수강 →(결과) 성적경고: 사실 문장 (근거: 안내 id)" — 조건이 있으면 뒤에 조건과 되묻기 기준. */
    private String line(Edge edge) {
        StringBuilder line = new StringBuilder("- ").append(name(edge.from())).append(" →(").append(edge.type()).append(") ")
                .append(name(edge.to())).append(": ").append(edge.fact().trim()).append(" (근거: ").append(edge.guide()).append(')');
        if (edge.condition() != null && !edge.condition().isEmpty()) {
            List<String> parts = new ArrayList<>();
            edge.condition().forEach((key, value) -> parts.add(Boolean.TRUE.equals(value) ? key : key + "=" + value));
            line.append(" (조건: ").append(String.join(", ", parts))
                    .append(" — 질문에서 알 수 없으면 갈리는 기준을 보이고 묻는다)");
        }
        return line.toString();
    }

    private String name(String nodeId) {
        Node node = nodes.get(nodeId);
        return node == null || node.name() == null || node.name().isBlank() ? nodeId : node.name();
    }

    /** 파일이 없거나 읽기에 실패하면 null(꺼짐). */
    static GraphFile read(JsonMapper jsonMapper, String resource) {
        ClassPathResource file = new ClassPathResource(resource);
        if (!file.exists()) {
            return null;
        }
        try (InputStream in = file.getInputStream()) {
            return jsonMapper.readValue(in, GraphFile.class);
        } catch (Exception e) {
            log.warn("물어보기 규정 그래프({})를 읽지 못해 끈다: {}", resource, e.toString());
            return null;
        }
    }

    /**
     * @param edgeIds 답 쓰기에 넘긴 선 id(기록용)
     * @param guides  answerIds 뒤에 더할 이웃 안내 id(원문을 함께 넘긴다)
     * @param block   context 끝에 붙일 [함께 볼 규정] 블록(앞 빈 줄 포함, 없으면 "")
     */
    public record Related(List<String> edgeIds, List<String> guides, String block) {
        static final Related EMPTY = new Related(List.of(), List.of(), "");

        public boolean isEmpty() {
            return edgeIds.isEmpty();
        }
    }

    record GraphFile(String version, String catalogVersion, List<Node> nodes, List<Edge> edges) {
    }

    record Node(String id, String name, List<String> guides) {
    }

    /**
     * 선 하나. id 칸이 없으면 '시작점>끝점:종류'를 id 로 쓴다(기록·로그용).
     * @param condition 선이 맞으려면 필요한 학생 상황({@link #CONDITION_KEYS} 칸만, 예: {"장학생": true}). 없으면 null.
     * @param reviewed  사람이 확인한 날짜·사람. 비어 있으면 읽지 않는다.
     */
    record Edge(String id, String from, String to, String type, String fact, String guide, String quote,
                Map<String, Object> condition, String reviewed) {
        @Override
        public String id() {
            return id == null || id.isBlank() ? from + ">" + to + ":" + type : id;
        }
    }
}
