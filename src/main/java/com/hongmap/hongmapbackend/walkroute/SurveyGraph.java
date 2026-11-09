package com.hongmap.hongmapbackend.walkroute;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 길찾기 경로망(경로망 조사 v3). `walkroute/survey-graph-v3.json` — 조사 도구 백업을 펼친 것(정점·간선·메모).
 * 앱에 들어 있던 surveyGraphV3.ts 를 그대로 옮겼다(11-09). 손으로 고치지 말고 조사 도구에서 고친 뒤 다시 만든다.
 */
record SurveyGraph(String exportedAt, List<Vertex> vertices, List<Edge> edges,
                   Map<String, String> nodeMemos, Map<String, String> edgeMemos) {

    static final String RESOURCE = "walkroute/survey-graph-v3.json";

    /** type: 0 기본, 1 출입구, 2 엘리베이터, 3 계단. code·floor 는 실외면 null, label 은 출입구만. */
    record Vertex(String id, double lat, double lng, String code, Double floor, int type, String label) {
    }

    /** kind: normal · stairs · stairs_vertical · elevator_vertical · tie. 양방향. */
    record Edge(String a, String b, String kind) {
    }

    static SurveyGraph load(JsonMapper mapper) {
        try (InputStream in = SurveyGraph.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " 이 없습니다");
            }
            return parse(mapper.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static SurveyGraph parse(JsonNode root) {
        List<Vertex> vertices = new ArrayList<>();
        for (JsonNode v : root.get("vertices")) {
            vertices.add(new Vertex(v.get(0).asString(), v.get(1).asDouble(), v.get(2).asDouble(),
                    v.get(3).isNull() ? null : v.get(3).asString(),
                    v.get(4).isNull() ? null : v.get(4).asDouble(),
                    v.get(5).asInt(),
                    v.get(6).isNull() ? null : v.get(6).asString()));
        }
        List<Edge> edges = new ArrayList<>();
        for (JsonNode e : root.get("edges")) {
            edges.add(new Edge(e.get(0).asString(), e.get(1).asString(), e.get(2).asString()));
        }
        return new SurveyGraph(root.get("exportedAt").asString(), List.copyOf(vertices), List.copyOf(edges),
                strings(root.get("nodeMemos")), strings(root.get("edgeMemos")));
    }

    private static Map<String, String> strings(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : node.properties()) {
            map.put(e.getKey(), e.getValue().asString());
        }
        return Collections.unmodifiableMap(map);
    }
}
