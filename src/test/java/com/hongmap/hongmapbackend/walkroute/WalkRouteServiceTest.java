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
    void 내_위치_출발용_approach_는_위치를_받지_않고_길_점마다_길을_준다() {
        WalkRouter.Approach approach = service.approach(t, 3.0);
        assertThat(approach.starts()).hasSizeGreaterThan(100);
        assertThat(approach.starts().get(0).route().points()).isNull();
        byte[] json = MAPPER.writeValueAsBytes(approach);
        System.out.println("approach JSON bytes: " + json.length);
        assertThat(json.length).isLessThan(600_000);
        assertThat(service.approach(t, 3.0)).isSameAs(approach);
    }

    @Test
    void 잘못된_요청() {
        assertThatThrownBy(() -> service.routes(r, null, r, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> service.routes(r, null, 999_999, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> service.routes(r, 500.0, t, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> service.routes(r, Double.NaN, t, null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
