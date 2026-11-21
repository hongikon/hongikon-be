package com.hongmap.hongmapbackend.walkroute;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 11-16: 길찾기 지도에 층 바뀌는 곳(출구·입구·계단·엘리베이터)을 찍는 표시, 바깥 계단 배수 3 → 1.5. */
class WalkRouterMarkersTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Set<String> KINDS = Set.of("exit", "enter", "pass", "floor", "stairs");
    private static List<CampusBuilding> buildings;
    private static WalkRouter router;
    private static WalkRouter oldStairs;

    @BeforeAll
    static void load() throws Exception {
        JsonNode fixture;
        try (InputStream in = new GZIPInputStream(WalkRouterMarkersTest.class.getClassLoader().getResourceAsStream("walkroute/route-parity.json.gz"))) {
            fixture = MAPPER.readTree(in);
        }
        buildings = new ArrayList<>();
        for (JsonNode node : fixture.get("buildings")) {
            CampusBuilding building = CampusBuilding.from(node);
            if (building != null) {
                buildings.add(building);
            }
        }
        SurveyGraph graph = SurveyGraph.load(MAPPER);
        router = new WalkRouter(graph, buildings);
        oldStairs = new WalkRouter(graph, buildings, 3, WalkRouter.FLOOR_TRANSIT_SECONDS);
    }

    private static CampusBuilding code(String code) {
        return buildings.stream().filter(b -> code.equals(b.code())).findFirst().orElseThrow();
    }

    @Test
    void 표시는_경로_위에_있고_종류와_글이_있다() {
        int routes = 0;
        int withMarkers = 0;
        for (CampusBuilding from : buildings) {
            for (CampusBuilding to : buildings) {
                if (from == to) {
                    continue;
                }
                for (WalkRoute route : router.routes(from, null, to, null)) {
                    if (route.isEstimate()) {
                        assertThat(route.markers()).isNull();
                        continue;
                    }
                    routes++;
                    Set<String> onRoute = new HashSet<>();
                    route.points().forEach(p -> onRoute.add(p.lat() + "," + p.lng()));
                    for (WalkRoute.Marker m : route.markers()) {
                        assertThat(onRoute).contains(m.lat() + "," + m.lng());
                        assertThat(KINDS).contains(m.kind());
                        assertThat(m.label()).isNotBlank();
                    }
                    if (!route.markers().isEmpty()) {
                        withMarkers++;
                    }
                }
            }
        }
        assertThat(routes).isGreaterThan(500);
        assertThat(withMarkers).isGreaterThan(routes * 9 / 10);
    }

    @Test
    void 건물을_나가고_들어가는_곳과_도착_층이_찍힌다() {
        WalkRoute route = router.routes(code("hongik_t"), 3.0, code("hongik_r"), 5.0).get(0);
        List<String> kinds = route.markers().stream().map(WalkRoute.Marker::kind).toList();
        assertThat(kinds).contains("exit");
        assertThat(kinds.get(0)).isIn("exit", "floor");
        assertThat(route.markers().stream().filter(m -> m.kind().equals("exit")).findFirst().orElseThrow().label()).startsWith("T동");
        WalkRoute.Marker last = route.markers().get(route.markers().size() - 1);
        assertThat(last.label()).contains("5층");
    }

    @Test
    void 바깥_계단_배수_15는_예전_3보다_오래_걸리지_않는다() {
        int faster = 0;
        for (CampusBuilding from : buildings) {
            for (CampusBuilding to : buildings) {
                if (from == to) {
                    continue;
                }
                List<WalkRoute> now = router.routes(from, null, to, null);
                List<WalkRoute> before = oldStairs.routes(from, null, to, null);
                assertThat(now.get(0).totalSeconds()).isLessThanOrEqualTo(before.get(0).totalSeconds() + 1e-9);
                if (now.get(0).totalSeconds() < before.get(0).totalSeconds() - 1) {
                    faster++;
                }
            }
        }
        assertThat(faster).isGreaterThan(0);
    }

    @Test
    void 내_위치가_건물_안일_때_쓰는_건물_출발_길은_건물_길과_같고_나가는_층이_찍힌다() {
        CampusBuilding t = code("hongik_t");
        CampusBuilding g = code("hongik_g");
        long started = System.nanoTime();
        WalkRouter.Approach approach = router.approach(g, null);
        System.out.println("approach ms = " + (System.nanoTime() - started) / 1_000_000 + ", buildings = " + approach.buildings().size());
        assertThat(approach.buildings()).noneMatch(b -> b.building() == g.id());
        WalkRouter.ApproachBuilding fromT = approach.buildings().stream().filter(b -> b.building() == t.id()).findFirst().orElseThrow();
        WalkRoute direct = router.routes(t, null, g, null).get(0);
        WalkRoute viaApproach = fromT.routes().get(0).route();
        assertThat(viaApproach.totalSeconds()).isCloseTo(direct.totalSeconds(), org.assertj.core.data.Offset.offset(1e-6));
        assertThat(viaApproach.markers()).anySatisfy(m -> {
            assertThat(m.kind()).isEqualTo("exit");
            assertThat(m.label()).startsWith("T동").contains("층");
        });
        WalkRouter.ApproachNode first = approach.nodes().get(fromT.routes().get(0).path().get(0));
        assertThat(first.lat()).isEqualTo(t.lat());
        assertThat(first.lng()).isEqualTo(t.lng());
    }

    @Test
    void 시설_전용_G동_남쪽_1층_건강진료센터_지하1층_라운지_출입구로는_드나들지_않는다() {
        CampusBuilding g = code("hongik_g");
        Set<Double> facilityDoors = Set.of(37.551079, 37.5511004);
        for (CampusBuilding other : buildings) {
            if (other == g) {
                continue;
            }
            for (Double floor : new Double[] {null, 1.0, -1.0}) {
                for (WalkRoute route : router.routes(other, null, g, floor)) {
                    if (route.markers() != null) {
                        assertThat(route.markers()).noneSatisfy(m -> assertThat(facilityDoors).contains(m.lat()));
                    }
                }
                for (WalkRoute route : router.routes(g, floor, other, null)) {
                    if (route.markers() != null) {
                        assertThat(route.markers()).noneSatisfy(m -> assertThat(facilityDoors).contains(m.lat()));
                    }
                }
            }
        }
    }

    @Test
    void 쓰지_않는_문헌관_지하1층_출입구_대신_1층_출입구로만_드나든다() {
        CampusBuilding mh = code("hongik_mh");
        double closedDoor = 37.5509733;   // n96 HI_MH_B1F_ENTER
        double openDoor = 37.5507229;     // n92 HI_MH_1F_ENTER
        int checked = 0;
        for (CampusBuilding other : buildings) {
            if (other == mh) {
                continue;
            }
            for (Double floor : new Double[] {null, 1.0, -1.0}) {
                List<WalkRoute> routes = new ArrayList<>(router.routes(other, null, mh, floor));
                routes.addAll(router.routes(mh, floor, other, null));
                for (WalkRoute route : routes) {
                    if (route.markers() == null) {
                        continue;
                    }
                    assertThat(route.markers()).noneSatisfy(m -> assertThat(m.lat()).isEqualTo(closedDoor));
                    assertThat(route.markers()).anySatisfy(m -> assertThat(m.lat()).isEqualTo(openDoor));
                    checked++;
                }
            }
        }
        assertThat(checked).isPositive();
    }

}
