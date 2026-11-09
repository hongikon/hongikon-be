package com.hongmap.hongmapbackend.walkroute;

import com.hongmap.hongmapbackend.mapdata.MapDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalkRouteServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private MapDataService mapData;
    private WalkRouteService service;
    private byte[] body;
    private long r;
    private long t;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode fixture;
        try (InputStream in = new GZIPInputStream(getClass().getClassLoader().getResourceAsStream("walkroute/route-parity.json.gz"))) {
            fixture = MAPPER.readTree(in);
        }
        ObjectNode payload = MAPPER.createObjectNode();
        payload.set("buildings", fixture.get("buildings"));
        body = MAPPER.writeValueAsBytes(payload);
        for (JsonNode b : fixture.get("buildings")) {
            if (b.get("name").asString().endsWith("R동")) {
                r = b.get("id").asLong();
            }
            if (b.get("name").asString().endsWith("T동")) {
                t = b.get("id").asLong();
            }
        }
        mapData = mock(MapDataService.class);
        when(mapData.current()).thenReturn(new MapDataService.Snapshot("v1", body));
        service = new WalkRouteService(mapData, MAPPER);
    }

    @Test
    void 건물에서_건물로_후보를_가장_빠른_길부터() {
        List<WalkRoute> routes = service.routes(r, 1.0, t, 3.0);
        assertThat(routes).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(routes.get(0).isEstimate()).isFalse();
        assertThat(routes.get(0).steps()).isNotEmpty();
        for (WalkRoute route : routes) {
            assertThat(route.totalSeconds()).isGreaterThanOrEqualTo(routes.get(0).totalSeconds());
        }
    }

    @Test
    void 같은_질문은_기억한_결과를_주고_지도_데이터가_바뀌면_다시_계산() {
        List<WalkRoute> first = service.routes(r, null, t, null);
        assertThat(service.routes(r, null, t, null)).isSameAs(first);
        when(mapData.current()).thenReturn(new MapDataService.Snapshot("v2", body));
        List<WalkRoute> again = service.routes(r, null, t, null);
        assertThat(again).isNotSameAs(first).isEqualTo(first);
    }

    @Test
    void 내_위치_출발용_approach_는_위치를_받지_않고_길_점마다_후보를_준다() throws Exception {
        long started = System.nanoTime();
        byte[] json = service.approachJson(t, 3.0);
        System.out.println("approach ms: " + (System.nanoTime() - started) / 1_000_000 + ", bytes: " + json.length);
        JsonNode approach = MAPPER.readTree(json);
        assertThat(approach.get("starts").size()).isGreaterThan(100);
        int withAlternatives = 0;
        for (JsonNode start : approach.get("starts")) {
            JsonNode routes = start.get("routes");
            assertThat(routes.size()).isBetween(1, 3);
            assertThat(routes.get(0).get("route").has("points")).isFalse();
            assertThat(routes.get(0).get("path").get(0).asInt()).isEqualTo(start.get("node").asInt());
            if (routes.size() > 1) {
                withAlternatives++;
            }
        }
        assertThat(withAlternatives).isGreaterThan(10);
        assertThat(json.length).isLessThan(1_500_000);
        assertThat(service.approachJson(t, 3.0)).isSameAs(json);
    }

    @Test
    void 잘못된_요청() {
        assertThatThrownBy(() -> service.routes(r, null, r, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> service.routes(r, null, 999_999, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> service.routes(r, 500.0, t, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        // 층 다이얼에 없는 층(아무 숫자)으로 새 계산을 시키지 못한다.
        assertThatThrownBy(() -> service.approachJson(t, 3.0001)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> service.approachJson(999_999, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> service.routes(r, Double.NaN, t, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
