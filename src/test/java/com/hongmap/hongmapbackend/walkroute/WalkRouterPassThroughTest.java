package com.hongmap.hongmapbackend.walkroute;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 11-17: 지나가는 길로 쓰지 않는 건물(Z1동) — 출발·도착일 때만 그 안을 쓴다. 작은 손 그래프. */
class WalkRouterPassThroughTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static SurveyGraph survey;
    private static CampusBuilding a;
    private static CampusBuilding z1;
    private static CampusBuilding b;
    private static WalkRouter router;

    /** A관 —o1— [Z1동 1층 서쪽 문 — 안 — 동쪽 문] —o2— B관. Z1동을 돌아가는 바깥 길(o1 → o3 → o2)은 더 길다. */
    @BeforeAll
    static void build() throws Exception {
        List<SurveyGraph.Vertex> vertices = List.of(
                indoor("a1@ba:1", 37.5500, 126.9200, "ba", 1),
                indoor("z1@hongik_z1:1", 37.5500, 126.9208, "hongik_z1", 1),
                indoor("z2@hongik_z1:1", 37.5500, 126.9212, "hongik_z1", 1),
                indoor("b1@bb:1", 37.5500, 126.9220, "bb", 1),
                outdoor("o1", 37.5500, 126.9204),
                outdoor("o2", 37.5500, 126.9216),
                outdoor("o3", 37.5506, 126.9210));
        List<SurveyGraph.Edge> edges = List.of(
                new SurveyGraph.Edge("a1@ba:1", "o1", "normal"),
                new SurveyGraph.Edge("o1", "z1@hongik_z1:1", "normal"),
                new SurveyGraph.Edge("z1@hongik_z1:1", "z2@hongik_z1:1", "normal"),
                new SurveyGraph.Edge("z2@hongik_z1:1", "o2", "normal"),
                new SurveyGraph.Edge("o2", "b1@bb:1", "normal"),
                new SurveyGraph.Edge("o1", "o3", "normal"),
                new SurveyGraph.Edge("o3", "o2", "normal"));
        survey = new SurveyGraph("2026-11-17T00:00:00Z", vertices, edges, Map.of(), Map.of());
        a = building("{\"id\":1,\"code\":\"ba\",\"name\":\"A관\",\"lat\":37.5500,\"lng\":126.9199,\"floors\":1}");
        z1 = building("{\"id\":2,\"code\":\"hongik_z1\",\"name\":\"제1강의동 Z1동\",\"lat\":37.5500,\"lng\":126.9210,\"floors\":3}");
        b = building("{\"id\":3,\"code\":\"bb\",\"name\":\"B관\",\"lat\":37.5500,\"lng\":126.9221,\"floors\":1}");
        router = new WalkRouter(survey, List.of(a, z1, b));
    }

    private static SurveyGraph.Vertex indoor(String id, double lat, double lng, String code, double floor) {
        return new SurveyGraph.Vertex(id, lat, lng, code, floor, 1, null);
    }

    private static SurveyGraph.Vertex outdoor(String id, double lat, double lng) {
        return new SurveyGraph.Vertex(id, lat, lng, null, null, 0, null);
    }

    private static CampusBuilding building(String json) throws Exception {
        return CampusBuilding.from(MAPPER.readTree(json));
    }

    @Test
    void Z1동을_지나지_않고_바깥으로_돈다() {
        List<WalkRoute> routes = router.routes(a, null, b, null);
        assertThat(routes).isNotEmpty().allSatisfy(r -> {
            assertThat(r.isEstimate()).isFalse();
            assertThat(r.viaBuildings()).noneMatch(name -> name.contains("Z1"));
            assertThat(r.points()).noneMatch(p -> p.lat() == 37.5500 && p.lng() == 126.9208);
        });
        // 예전 계산(비교 테스트용)은 Z1동 안을 지난다 — 막는 것이 실제로 길을 바꾼다는 확인.
        WalkRouter old = new WalkRouter(survey, List.of(a, z1, b), WalkRouter.STAIRS_TIME_FACTOR, WalkRouter.FLOOR_TRANSIT_SECONDS, true);
        assertThat(old.routes(a, null, b, null).get(0).points()).anyMatch(p -> p.lat() == 37.5500 && p.lng() == 126.9208);
    }

    @Test
    void Z1동이_출발_도착이면_그_안을_쓴다() {
        assertThat(router.routes(a, null, z1, null).get(0).isEstimate()).isFalse();
        assertThat(router.routes(z1, null, b, null).get(0).isEstimate()).isFalse();
        WalkRouter.Approach approach = router.approach(b, null);
        assertThat(approach.buildings()).anyMatch(x -> x.building() == z1.id());
        assertThat(approach.buildings()).anyMatch(x -> x.building() == a.id());
        assertThat(router.approach(z1, null).starts()).isNotEmpty();
    }

    @Test
    void 내_위치_출발도_Z1동을_지나지_않는다() {
        WalkRouter.Approach approach = router.approach(b, null);
        int z1Index = -1;
        for (int i = 0; i < approach.nodes().size(); i++) {
            if (approach.nodes().get(i).id().startsWith("z1@")) {
                z1Index = i;
            }
        }
        int blocked = z1Index;
        assertThat(approach.starts()).allSatisfy(s -> assertThat(s.routes()).allSatisfy(r -> assertThat(r.path()).doesNotContain(blocked)));
        WalkRouter.ApproachBuilding fromA = approach.buildings().stream().filter(x -> x.building() == a.id()).findFirst().orElseThrow();
        assertThat(fromA.routes()).allSatisfy(r -> assertThat(r.path()).doesNotContain(blocked));
    }
}
