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

/**
 * 건물 안에서 층을 옮겨 다닐 길이 없는 건물(11-20 사용자). 강당 S동은 1층·3층 출입구가 따로 있고 안에서 이어지지 않아,
 * 3층으로 가는 길은 1층으로 들어가 "계단·엘리베이터로 3층 올라가기"가 아니라 바깥으로 3층 출입구까지 가야 한다.
 */
class WalkRouterNoIndoorFloorChangeTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static WalkRouter router;
    private static CampusBuilding s;
    private static CampusBuilding t;

    @BeforeAll
    static void load() throws Exception {
        JsonNode fixture;
        try (InputStream in = new GZIPInputStream(WalkRouterNoIndoorFloorChangeTest.class.getClassLoader().getResourceAsStream("walkroute/route-parity.json.gz"))) {
            fixture = MAPPER.readTree(in);
        }
        List<CampusBuilding> buildings = new ArrayList<>();
        for (JsonNode node : fixture.get("buildings")) {
            CampusBuilding building = CampusBuilding.from(node);
            buildings.add(building);
            switch (building.code()) {
                case "hongik_s" -> s = building;
                case "hongik_t" -> t = building;
                default -> { }
            }
        }
        router = new WalkRouter(SurveyGraph.load(MAPPER), buildings);
    }

    @Test
    void S동_3층으로_가는_길은_어느_후보도_건물_안에서_층을_오르지_않는다() {
        List<WalkRoute> routes = router.routes(t, null, s, 3.0);
        assertThat(routes).isNotEmpty();
        for (WalkRoute route : routes) {
            assertThat(route.steps()).noneMatch(step -> step.startsWith("계단·엘리베이터로"));
            assertThat(route.steps().get(route.steps().size() - 1)).contains("3층");
        }
    }

    @Test
    void S동_1층으로_가는_길은_그대로_1층_출입구() {
        List<WalkRoute> routes = router.routes(t, null, s, 1.0);
        assertThat(routes).isNotEmpty();
        assertThat(routes.get(0).steps().get(routes.get(0).steps().size() - 1)).contains("1층");
        assertThat(routes.get(0).steps()).noneMatch(step -> step.startsWith("계단·엘리베이터로"));
    }

    @Test
    void S동_3층에서_출발해도_1층_출입구로_나가_층을_내려가지_않는다() {
        List<WalkRoute> routes = router.routes(s, 3.0, t, null);
        assertThat(routes).isNotEmpty();
        for (WalkRoute route : routes) {
            assertThat(route.steps()).noneMatch(step -> step.startsWith("계단·엘리베이터로"));
        }
    }
}
