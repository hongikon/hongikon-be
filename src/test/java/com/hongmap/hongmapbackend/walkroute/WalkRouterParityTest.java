package com.hongmap.hongmapbackend.walkroute;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앱 길찾기(src/utils/routing.ts, 11-09)와 같은 결과를 내는지. route-parity.json.gz 는 운영 지도 데이터(11-09)로 앱 findRoutes 를
 * 모든 건물 쌍(층 안 고름·맨 아래→맨 위·맨 위→맨 아래)에 돌린 결과다. 경로망이나 규칙을 바꾸면 이 파일도 다시 만든다(worklog 11-09).
 */
class WalkRouterParityTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static WalkRouter router;
    private static Map<Long, CampusBuilding> byId;
    private static JsonNode cases;

    @BeforeAll
    static void load() throws Exception {
        JsonNode fixture;
        try (InputStream in = new GZIPInputStream(WalkRouterParityTest.class.getClassLoader()
                .getResourceAsStream("walkroute/route-parity.json.gz"))) {
            fixture = MAPPER.readTree(in);
        }
        List<CampusBuilding> buildings = new ArrayList<>();
        byId = new HashMap<>();
        for (JsonNode b : fixture.get("buildings")) {
            CampusBuilding building = CampusBuilding.from(b);
            if (building != null) {
                buildings.add(building);
                byId.put(building.id(), building);
            }
        }
        router = new WalkRouter(SurveyGraph.load(MAPPER), buildings);
        cases = fixture.get("cases");
    }

    private static Double floor(JsonNode n) {
        return n == null || n.isNull() ? null : n.asDouble();
    }

    @Test
    void 앱과_같은_길찾기_결과() {
        List<String> mismatches = new ArrayList<>();
        int total = 0;
        for (JsonNode c : cases) {
            total++;
            CampusBuilding from = byId.get(c.get("from").asLong());
            CampusBuilding to = byId.get(c.get("to").asLong());
            List<WalkRoute> actual = router.routes(from, floor(c.get("fromFloor")), to, floor(c.get("toFloor")));
            String diff = compare(c.get("routes"), actual);
            if (diff != null) {
                mismatches.add(from.name() + "(" + c.get("fromFloor") + ") → " + to.name() + "(" + c.get("toFloor") + "): " + diff);
            }
        }
        assertThat(total).isGreaterThan(1000);
        assertThat(mismatches).as("앱과 다른 경우 %d / %d", mismatches.size(), total).isEmpty();
    }

    private static String compare(JsonNode expected, List<WalkRoute> actual) {
        if (expected.size() != actual.size()) {
            return "후보 수 " + expected.size() + " ≠ " + actual.size();
        }
        for (int i = 0; i < actual.size(); i++) {
            JsonNode e = expected.get(i);
            WalkRoute a = actual.get(i);
            String at = "#" + i + " ";
            if (Math.abs(e.get("totalSeconds").asDouble() - Math.round(a.totalSeconds() * 1000) / 1000.0) > 1e-9) {
                return at + "시간 " + e.get("totalSeconds") + " ≠ " + a.totalSeconds();
            }
            if (e.get("distanceMeters").asLong() != a.distanceMeters() || e.get("minutes").asLong() != a.minutes()) {
                return at + "거리·분 " + e.get("distanceMeters") + "/" + e.get("minutes") + " ≠ " + a.distanceMeters() + "/" + a.minutes();
            }
            if (e.get("hasIndoorTransit").asBoolean() != a.hasIndoorTransit() || e.get("hasStairs").asBoolean() != a.hasStairs()
                    || e.get("hasLinkSegment").asBoolean() != a.hasLinkSegment() || e.get("isEstimate").asBoolean() != a.isEstimate()) {
                return at + "표시(실내·계단·연결·어림)";
            }
            if (!same(e.get("usesElevator"), a.usesElevator()) || !same(e.get("indoorMeters"), a.indoorMeters())) {
                return at + "엘리베이터·실내 거리";
            }
            if (!java.util.Objects.equals(strings(e.get("viaBuildings")), a.viaBuildings())) {
                return at + "지나는 건물 " + strings(e.get("viaBuildings")) + " ≠ " + a.viaBuildings();
            }
            if (!java.util.Objects.equals(strings(e.get("notes")), a.notes())) {
                return at + "메모 " + strings(e.get("notes")) + " ≠ " + a.notes();
            }
            if (!java.util.Objects.equals(strings(e.get("steps")), a.steps())) {
                return at + "길 흐름 " + strings(e.get("steps")) + " ≠ " + a.steps();
            }
            if (!points(e.get("points")).equals(pointList(a.points()))) {
                return at + "점 " + e.get("points").size() + " ≠ " + a.points().size();
            }
            List<List<List<Double>>> segs = new ArrayList<>();
            if (a.indoorSegments() != null) {
                for (List<WalkRoute.Point> s : a.indoorSegments()) {
                    segs.add(pointList(s));
                }
            }
            List<List<List<Double>>> expectedSegs = new ArrayList<>();
            if (!e.get("indoorSegments").isNull()) {
                for (JsonNode s : e.get("indoorSegments")) {
                    expectedSegs.add(points(s));
                }
            }
            if (!expectedSegs.equals(segs) || e.get("indoorSegments").isNull() != (a.indoorSegments() == null)) {
                return at + "실내 구간";
            }
        }
        return null;
    }

    private static boolean same(JsonNode e, Object a) {
        if (e == null || e.isNull()) {
            return a == null;
        }
        if (a instanceof Boolean b) {
            return e.asBoolean() == b;
        }
        return a != null && e.asLong() == ((Number) a).longValue();
    }

    private static List<String> strings(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        n.forEach(x -> out.add(x.asString()));
        return out;
    }

    private static List<List<Double>> points(JsonNode n) {
        List<List<Double>> out = new ArrayList<>();
        n.forEach(p -> out.add(List.of(p.get(0).asDouble(), p.get(1).asDouble())));
        return out;
    }

    private static List<List<Double>> pointList(List<WalkRoute.Point> points) {
        List<List<Double>> out = new ArrayList<>();
        points.forEach(p -> out.add(List.of(p.lat(), p.lng())));
        return out;
    }
}
