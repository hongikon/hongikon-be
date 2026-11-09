package com.hongmap.hongmapbackend.walkroute;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내 위치 출발: 서버가 낸 approach(모든 길 점 → 도착 후보)에서 앱이 고르는 방식(150m 안 가까운 3곳, 걷는 시간 더해 가장 빠른 점의
 * 후보)이 예전 앱 계산(findRoutesFromPoint)과 같은 가장 빠른 길을 내는지, 후보가 3개 이하이고 가장 빠른 길이 맨 앞인지. approach-parity.json.gz 는 길 점 근처 약 25m 떨어진 위치들에서
 * 모든 건물(층 안 고름·맨 위층)로 앱이 계산한 결과(11-09).
 */
class WalkRouterApproachParityTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    static final double MY_LOCATION_MAX_METERS = 150;
    static final int MY_LOCATION_LINKS = 3;

    private static JsonNode read(String name) throws Exception {
        try (InputStream in = new GZIPInputStream(WalkRouterApproachParityTest.class.getClassLoader().getResourceAsStream(name))) {
            return MAPPER.readTree(in);
        }
    }

    /** 앱(src/apis/routes.ts 의 pickApproachRoutes)과 같은 고르기. 가장 빠른 길이 맨 앞. */
    static List<WalkRoute> pick(WalkRouter.Approach approach, double lat, double lng) {
        record Near(WalkRouter.ApproachStart start, double meters) {
        }
        List<Near> near = new ArrayList<>();
        for (WalkRouter.ApproachStart start : approach.starts()) {
            WalkRouter.ApproachNode node = approach.nodes().get(start.node());
            double meters = Geo.haversineMeters(lat, lng, node.lat(), node.lng());
            if (meters <= MY_LOCATION_MAX_METERS) {
                near.add(new Near(start, meters));
            }
        }
        near.sort(Comparator.comparingDouble(Near::meters));
        Near best = null;
        double bestSeconds = Double.POSITIVE_INFINITY;
        for (Near n : near.subList(0, Math.min(MY_LOCATION_LINKS, near.size()))) {
            double seconds = n.meters() / WalkRouter.WALKING_METERS_PER_MINUTE * 60 + n.start().routes().get(0).route().totalSeconds();
            if (seconds < bestSeconds) {
                best = n;
                bestSeconds = seconds;
            }
        }
        if (best == null) {
            return List.of();
        }
        double walkSeconds = best.meters() / WalkRouter.WALKING_METERS_PER_MINUTE * 60;
        List<WalkRoute> out = new ArrayList<>();
        for (WalkRouter.ApproachRoute candidate : best.start().routes()) {
            List<WalkRoute.Point> points = new ArrayList<>();
            points.add(new WalkRoute.Point(lat, lng));
            for (int i : candidate.path()) {
                WalkRouter.ApproachNode node = approach.nodes().get(i);
                WalkRoute.Point last = points.get(points.size() - 1);
                if (last.lat() != node.lat() || last.lng() != node.lng()) {
                    points.add(new WalkRoute.Point(node.lat(), node.lng()));
                }
            }
            WalkRoute r = candidate.route();
            double seconds = walkSeconds + r.totalSeconds();
            out.add(new WalkRoute(points, Math.round(best.meters() + r.distanceMeters()), seconds, Math.max(1, Math.round(seconds / 60)),
                    r.hasIndoorTransit(), r.hasStairs(), r.hasLinkSegment(), false, r.usesElevator(), r.indoorMeters(), r.viaBuildings(),
                    r.notes(), r.steps(), r.indoorSegments()));
        }
        return out;
    }

    @Test
    void 내_위치_출발이_예전_앱_계산과_같다() throws Exception {
        JsonNode routeFixture = read("walkroute/route-parity.json.gz");
        List<CampusBuilding> buildings = new ArrayList<>();
        Map<Long, CampusBuilding> byId = new HashMap<>();
        for (JsonNode b : routeFixture.get("buildings")) {
            CampusBuilding building = CampusBuilding.from(b);
            if (building != null) {
                buildings.add(building);
                byId.put(building.id(), building);
            }
        }
        WalkRouter router = new WalkRouter(SurveyGraph.load(MAPPER), buildings);
        Map<String, List<JsonNode>> byTarget = new java.util.LinkedHashMap<>();
        for (JsonNode c : read("walkroute/approach-parity.json.gz").get("cases")) {
            byTarget.computeIfAbsent(c.get("to").asLong() + "|" + c.get("toFloor"), k -> new ArrayList<>()).add(c);
        }
        List<String> mismatches = new ArrayList<>();
        int total = 0;
        for (List<JsonNode> group : byTarget.values()) {
            CampusBuilding to = byId.get(group.get(0).get("to").asLong());
            Double toFloor = group.get(0).get("toFloor").isNull() ? null : group.get(0).get("toFloor").asDouble();
            WalkRouter.Approach approach = router.approach(to, toFloor);
            for (JsonNode c : group) {
                total++;
                List<WalkRoute> picked = pick(approach, c.get("me").get(0).asDouble(), c.get("me").get(1).asDouble());
                WalkRoute actual = picked.isEmpty() ? null : picked.get(0);
                if (picked.size() > 3 || (picked.size() > 1 && picked.subList(1, picked.size()).stream()
                        .anyMatch(r -> r.totalSeconds() < picked.get(0).totalSeconds()))) {
                    mismatches.add(to.name() + ": 후보 수·순서");
                }
                JsonNode e = c.get("route");
                String where = to.name() + "(" + toFloor + ") from " + c.get("me");
                if (e.isNull() || actual == null) {
                    if (e.isNull() != (actual == null)) {
                        mismatches.add(where + ": 있음/없음 다름");
                    }
                    continue;
                }
                List<String> steps = new ArrayList<>();
                e.get("steps").forEach(s -> steps.add(s.asString()));
                List<String> notes = new ArrayList<>();
                e.get("notes").forEach(s -> notes.add(s.asString()));
                List<String> via = new ArrayList<>();
                e.get("viaBuildings").forEach(s -> via.add(s.asString()));
                if (Math.abs(e.get("totalSeconds").asDouble() - actual.totalSeconds()) > 1e-3
                        || e.get("minutes").asLong() != actual.minutes()
                        || Math.abs(e.get("distanceMeters").asLong() - actual.distanceMeters()) > 1
                        || e.get("points").size() != actual.points().size()
                        || !steps.equals(actual.steps()) || !notes.equals(actual.notes()) || !via.equals(actual.viaBuildings())
                        || e.get("hasStairs").asBoolean() != actual.hasStairs()
                        || !Objects.equals(e.get("usesElevator").asBoolean(), actual.usesElevator())) {
                    mismatches.add(where + ": " + e.get("totalSeconds") + "s " + e.get("steps") + " ≠ " + actual.totalSeconds() + "s " + actual.steps());
                }
            }
        }
        assertThat(total).isGreaterThan(1000);
        assertThat(mismatches).as("다른 경우 %d / %d", mismatches.size(), total).isEmpty();
    }
}
