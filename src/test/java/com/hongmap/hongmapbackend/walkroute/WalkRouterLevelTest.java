package com.hongmap.hongmapbackend.walkroute;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 층 목록(floor_levels)과 이어 붙은 건물(인문사회관 A·B동)의 같은 층(11-10). */
class WalkRouterLevelTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static WalkRouter router;
    private static CampusBuilding a;
    private static CampusBuilding b;
    private static CampusBuilding t;

    @BeforeAll
    static void load() throws Exception {
        JsonNode fixture;
        try (InputStream in = new GZIPInputStream(WalkRouterLevelTest.class.getClassLoader().getResourceAsStream("walkroute/route-parity.json.gz"))) {
            fixture = MAPPER.readTree(in);
        }
        List<CampusBuilding> buildings = new ArrayList<>();
        for (JsonNode node : fixture.get("buildings")) {
            CampusBuilding building = CampusBuilding.from(node);
            buildings.add(building);
            switch (building.code()) {
                case "hongik_a" -> a = building;
                case "hongik_b" -> b = building;
                case "hongik_t" -> t = building;
                default -> { }
            }
        }
        router = new WalkRouter(SurveyGraph.load(MAPPER), buildings);
    }

    @Test
    void A동_3층_이상은_B동_그_층으로_B동_지하는_A동으로() {
        assertThat(router.sameLevel(a, 5.0)).isEqualTo(new WalkRouter.Level(b, 3.0));
        assertThat(router.sameLevel(a, 3.0)).isEqualTo(new WalkRouter.Level(b, 1.0));
        assertThat(router.sameLevel(a, 2.0)).isEqualTo(new WalkRouter.Level(a, 2.0));
        assertThat(router.sameLevel(b, -2.0)).isEqualTo(new WalkRouter.Level(a, 1.0));
        assertThat(router.sameLevel(b, -1.0)).isEqualTo(new WalkRouter.Level(a, 2.0));
        assertThat(router.sameLevel(b, 2.0)).isEqualTo(new WalkRouter.Level(b, 2.0));
        assertThat(router.sameLevel(a, null)).isEqualTo(new WalkRouter.Level(a, null));
    }

    @Test
    void A동_5층으로_가는_길은_B동_3층으로_안내() {
        List<WalkRoute> routes = router.routes(t, null, a, 5.0);
        assertThat(routes.get(0).isEstimate()).isFalse();
        List<String> steps = routes.get(0).steps();
        assertThat(steps.get(steps.size() - 1)).isEqualTo("B동 3층(A동 5층)");
        assertThat(routes.get(0).notes()).contains("A동 5층 = B동 3층");
        // 층 이동은 A동 번호로 그대로(A동 5층 = B동 3층 같은 층이라 따로 내려가지 않는다).
        assertThat(String.join(" / ", steps)).doesNotContain("3층 내려가기");
    }

    @Test
    void 층_목록이_있으면_그대로_층_다이얼() throws Exception {
        CampusBuilding r = CampusBuilding.from(MAPPER.readTree(
                "{\"name\":\"홍문관 R동\",\"lat\":37.55,\"lng\":126.92,\"floors\":16,\"basementFloors\":6,\"floorLevels\":[-6,-5,-4,-3,-2,0,1,2,16]}"));
        assertThat(Floors.floorOptions(r)).containsExactly(-6.0, -5.0, -4.0, -3.0, -2.0, 0.0, 1.0, 2.0, 16.0);
        CampusBuilding range = CampusBuilding.from(MAPPER.readTree(
                "{\"name\":\"X\",\"lat\":37.55,\"lng\":126.92,\"floors\":3,\"basementFloors\":1}"));
        assertThat(Floors.floorOptions(range)).containsExactly(-1.0, 1.0, 2.0, 3.0);
    }
}
