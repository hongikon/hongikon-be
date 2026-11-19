package com.hongmap.hongmapbackend.walkroute;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 11-18: 2·3순위 후보가 1순위의 짧은 구간 하나만 피해 옆 점으로 살짝 돌아가는 혹(운영 v19 n354 → n229 → n644)을 만들지 않는다.
 * A관 —o1— m —o3— B관, o1 과 o3 사이에 북쪽으로 11m 튀는 점 s(o1 → s → o3 이 6m 더 김).
 */
class WalkRouterSmoothTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void 짧은_혹으로_돌아가는_후보는_가장_빠른_길로_펴져_없어진다() throws Exception {
        List<SurveyGraph.Vertex> vertices = List.of(
                new SurveyGraph.Vertex("a1@ba:1", 37.5500, 126.9200, "ba", 1.0, 1, null),
                new SurveyGraph.Vertex("b1@bb:1", 37.5500, 126.9206, "bb", 1.0, 1, null),
                outdoor("o1", 37.5500, 126.9201),
                outdoor("m", 37.5500, 126.9203),
                outdoor("s", 37.5501, 126.9203),
                outdoor("o3", 37.5500, 126.9205));
        List<SurveyGraph.Edge> edges = List.of(
                new SurveyGraph.Edge("a1@ba:1", "o1", "normal"),
                new SurveyGraph.Edge("o1", "m", "normal"),
                new SurveyGraph.Edge("m", "o3", "normal"),
                new SurveyGraph.Edge("o1", "s", "normal"),
                new SurveyGraph.Edge("s", "o3", "normal"),
                new SurveyGraph.Edge("o3", "b1@bb:1", "normal"));
        SurveyGraph survey = new SurveyGraph("2026-11-18T00:00:00Z", vertices, edges, Map.of(), Map.of());
        CampusBuilding a = CampusBuilding.from(MAPPER.readTree("{\"id\":1,\"code\":\"ba\",\"name\":\"A관\",\"lat\":37.5500,\"lng\":126.9199,\"floors\":1}"));
        CampusBuilding b = CampusBuilding.from(MAPPER.readTree("{\"id\":2,\"code\":\"bb\",\"name\":\"B관\",\"lat\":37.5500,\"lng\":126.9207,\"floors\":1}"));

        List<WalkRoute> routes = new WalkRouter(survey, List.of(a, b)).routes(a, null, b, null);
        assertThat(routes).isNotEmpty().allSatisfy(r -> assertThat(r.points()).noneMatch(p -> p.lat() == 37.5501));

        // 예전 계산(비교 테스트용)은 펴지 않아 혹 후보가 남는다 — 펴기가 실제로 후보를 바꾼다는 확인.
        WalkRouter old = new WalkRouter(survey, List.of(a, b), WalkRouter.STAIRS_TIME_FACTOR, WalkRouter.FLOOR_TRANSIT_SECONDS, true);
        assertThat(old.routes(a, null, b, null)).anySatisfy(r -> assertThat(r.points()).anyMatch(p -> p.lat() == 37.5501));
    }

    private static SurveyGraph.Vertex outdoor(String id, double lat, double lng) {
        return new SurveyGraph.Vertex(id, lat, lng, null, null, 0, null);
    }
}
