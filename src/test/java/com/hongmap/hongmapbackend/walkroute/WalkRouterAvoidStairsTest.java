package com.hongmap.hongmapbackend.walkroute;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계단 피하기(avoidStairs, 11-16). 작은 경로망:
 * <ul>
 *   <li>X관(bx): 1층 출입구 ↔ 2층은 계단(20초)과 엘리베이터(34초 + 걷기) 둘 다.</li>
 *   <li>Y관(by): 1층 출입구 ↔ 2층은 계단만.</li>
 *   <li>Z관(bz): X관에서 바깥 계단 지름길(o1–o2)과 돌아가는 평지 길(o1–o4–o2). 1층만 조사(2·3층은 조사 안 됨).</li>
 *   <li>W관: 경로망에서 1km 넘게 떨어져 이어지지 않는다.</li>
 * </ul>
 */
class WalkRouterAvoidStairsTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static WalkRouter router;
    private static CampusBuilding x;
    private static CampusBuilding y;
    private static CampusBuilding z;
    private static CampusBuilding w;

    @BeforeAll
    static void build() throws Exception {
        List<SurveyGraph.Vertex> vertices = List.of(
                indoor("n1@bx:1", 37.5500, 126.9202, "bx", 1, 1),
                indoor("n2@bx:2", 37.5500, 126.9201, "bx", 2, 0),
                indoor("n3@bx:1", 37.5500, 126.9200, "bx", 1, 2),
                indoor("n4@bx:2", 37.5500, 126.9200, "bx", 2, 2),
                indoor("n10@by:1", 37.5508, 126.9200, "by", 1, 1),
                indoor("n11@by:2", 37.5508, 126.9200, "by", 2, 0),
                indoor("n20@bz:1", 37.5500, 126.9228, "bz", 1, 1),
                outdoor("o1", 37.5500, 126.9205),
                outdoor("o2", 37.5500, 126.9215),
                outdoor("o3", 37.5500, 126.9225),
                outdoor("o4", 37.5510, 126.9210),
                outdoor("o5", 37.5506, 126.9200));
        List<SurveyGraph.Edge> edges = List.of(
                new SurveyGraph.Edge("n1@bx:1", "n2@bx:2", "stairs_vertical"),
                new SurveyGraph.Edge("n1@bx:1", "n3@bx:1", "normal"),
                new SurveyGraph.Edge("n3@bx:1", "n4@bx:2", "elevator_vertical"),
                new SurveyGraph.Edge("n4@bx:2", "n2@bx:2", "normal"),
                new SurveyGraph.Edge("n10@by:1", "n11@by:2", "stairs_vertical"),
                new SurveyGraph.Edge("n1@bx:1", "o1", "normal"),
                new SurveyGraph.Edge("o1", "o2", "stairs"),
                new SurveyGraph.Edge("o1", "o4", "normal"),
                new SurveyGraph.Edge("o4", "o2", "normal"),
                new SurveyGraph.Edge("o2", "o3", "normal"),
                new SurveyGraph.Edge("o3", "n20@bz:1", "normal"),
                new SurveyGraph.Edge("o1", "o5", "normal"),
                new SurveyGraph.Edge("o5", "n10@by:1", "normal"));
        SurveyGraph survey = new SurveyGraph("2026-11-16T00:00:00Z", vertices, edges, Map.of(), Map.of());
        x = building("{\"id\":1,\"code\":\"bx\",\"name\":\"X관\",\"lat\":37.5500,\"lng\":126.9201,\"floors\":2}");
        y = building("{\"id\":2,\"code\":\"by\",\"name\":\"Y관\",\"lat\":37.5509,\"lng\":126.9200,\"floors\":2}");
        z = building("{\"id\":3,\"code\":\"bz\",\"name\":\"Z관\",\"lat\":37.5500,\"lng\":126.9229,\"floors\":3}");
        w = building("{\"id\":4,\"name\":\"W관\",\"lat\":37.5600,\"lng\":126.9300,\"floors\":1}");
        router = new WalkRouter(survey, List.of(x, y, z, w));
    }

    private static SurveyGraph.Vertex indoor(String id, double lat, double lng, String code, double floor, int type) {
        return new SurveyGraph.Vertex(id, lat, lng, code, floor, type, null);
    }

    private static SurveyGraph.Vertex outdoor(String id, double lat, double lng) {
        return new SurveyGraph.Vertex(id, lat, lng, null, null, 0, null);
    }

    private static CampusBuilding building(String json) throws Exception {
        return CampusBuilding.from(MAPPER.readTree(json));
    }

    @Test
    void 계단을_피하면_엘리베이터와_평지_길만() {
        List<WalkRoute> normal = router.routes(x, 2.0, z, null);
        assertThat(normal.get(0).isEstimate()).isFalse();
        assertThat(normal.get(0).hasStairs()).isTrue();

        List<WalkRoute> avoid = router.routes(x, 2.0, z, null, true);
        assertThat(avoid).isNotEmpty();
        for (WalkRoute route : avoid) {
            assertThat(route.isEstimate()).isFalse();
            assertThat(route.hasStairs()).isFalse();
            assertThat(route.usesElevator()).isTrue();
            // 조사된 층(X관 2층 → Z관 출입구 층)만 지나 계단 없음이 확인된 길 — 표시 없음.
            assertThat(route.stairFreeUnverified()).isNull();
        }
    }

    @Test
    void 기존_시그니처는_계단_허용과_같다() {
        assertThat(router.routes(x, 2.0, z, null)).isEqualTo(router.routes(x, 2.0, z, null, false));
        assertThat(router.findRoutes(x, 2.0, z, null, 3)).isEqualTo(router.findRoutes(x, 2.0, z, null, 3, false));
    }

    @Test
    void 계단_말고_갈_길이_없으면_빈_목록_직선_어림_아님() {
        List<WalkRoute> normal = router.routes(z, null, y, 2.0);
        assertThat(normal).isNotEmpty();
        assertThat(normal.get(0).isEstimate()).isFalse();
        assertThat(normal.get(0).hasStairs()).isTrue();

        assertThat(router.routes(z, null, y, 2.0, true)).isEmpty();
        // 같은 건물 1층(출입구 층)은 계단 없이 갈 수 있다.
        assertThat(router.routes(z, null, y, 1.0, true)).isNotEmpty().allSatisfy(r -> assertThat(r.hasStairs()).isFalse());
    }

    @Test
    void 조사되지_않은_층은_계단을_피해도_층_이동_어림으로_간다() {
        // 그 층에 조사된 정점이 없으면 출입구에서 층 차이 어림(계단·엘리베이터)으로만 갈 수 있어 그대로 둔다.
        List<WalkRoute> avoid = router.routes(x, null, z, 3.0, true);
        assertThat(avoid).isNotEmpty().allSatisfy(r -> {
            assertThat(r.isEstimate()).isFalse();
            assertThat(r.hasStairs()).isFalse();
            assertThat(r.stairFreeUnverified()).isTrue();
        });
        assertThat(MAPPER.writeValueAsString(avoid)).contains("\"stairFreeUnverified\":true");
        // 출발 쪽만 조사돼 있어도 도착이 조사 안 된 층이면 표시한다.
        assertThat(router.routes(x, 2.0, z, 3.0, true)).isNotEmpty().allSatisfy(r -> assertThat(r.stairFreeUnverified()).isTrue());
    }

    @Test
    void 계단을_허용하면_계단_없음_표시는_늘_없다() {
        for (List<WalkRoute> routes : List.of(router.routes(x, null, z, 3.0), router.routes(x, 2.0, z, null),
                router.routes(z, null, y, 2.0, false), router.routes(x, null, w, null), router.routes(x, null, w, null, true))) {
            assertThat(routes).isNotEmpty().allSatisfy(r -> assertThat(r.stairFreeUnverified()).isNull());
            assertThat(MAPPER.writeValueAsString(routes)).doesNotContain("stairFreeUnverified");
        }
    }

    @Test
    void 경로망이_못_덮는_구간은_계단을_피해도_직선_어림_하나() {
        List<WalkRoute> avoid = router.routes(x, null, w, null, true);
        assertThat(avoid).hasSize(1);
        assertThat(avoid.get(0).isEstimate()).isTrue();
        assertThat(avoid).isEqualTo(router.routes(x, null, w, null));
    }
}
