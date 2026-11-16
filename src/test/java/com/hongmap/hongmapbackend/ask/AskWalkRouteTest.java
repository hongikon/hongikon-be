package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteResponse;
import com.hongmap.hongmapbackend.mapdata.MapDataService;
import com.hongmap.hongmapbackend.walkroute.WalkRoute;
import com.hongmap.hongmapbackend.walkroute.WalkRouteService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 11-15: '제4공학관에서 홍문관 가는 길'에 출발을 버리고 '안내에 없어요'라고 했다 — 지도 길찾기 경로망으로 답하고 출발 건물을 버튼에 싣는다. */
class AskWalkRouteTest {

    private static final String DATA = """
            {"buildings":[
              {"id":19,"code":"hongik_t","name":"제4공학관 T동","lat":37.5510,"lng":126.9240},
              {"id":1,"code":"hongik_r","name":"홍문관 R동","lat":37.5540,"lng":126.9240},
              {"id":2,"code":"hongik_k","name":"제1공학관 K동","lat":37.5520,"lng":126.9240}],
             "facilities":[],"partners":[]}
            """;

    private static WalkRoute route(long minutes, long meters, boolean estimate, List<String> steps) {
        return new WalkRoute(List.of(), meters, minutes * 60.0, minutes, false, true, false, estimate, false, null,
                List.of("제1공학관 K동"), List.of("비 오면 K동 안으로"), steps, List.of());
    }

    private static AskLiveData liveData(WalkRouteService routes) {
        MapDataService map = mock(MapDataService.class);
        when(map.current()).thenReturn(new MapDataService.Snapshot("v", DATA.getBytes(StandardCharsets.UTF_8)));
        return new AskLiveData(null, null, map, JsonMapper.builder().build(), routes);
    }

    @Test
    void 두_건물_사이_걷는_시간과_길_흐름을_준다() {
        WalkRouteService routes = mock(WalkRouteService.class);
        when(routes.routes(19L, null, 1L, null)).thenReturn(List.of(
                route(4, 312, false, List.of("T동 1층 출구", "K동 1층 통과", "R동 1층 입구")), route(6, 400, false, List.of())));
        StringBuilder text = new StringBuilder();

        List<AskLiveData.Reference> places = liveData(routes).walkRoute("제4공학관", "홍문관", text);

        assertThat(places).extracting(AskLiveData.Reference::ref).containsExactly("building:hongik_t", "building:hongik_r");
        assertThat(text.toString()).contains("걸어서 약 4분 · 약 310m").contains("계단 있음").contains("안으로 지나는 건물: 제1공학관 K동")
                .contains("T동 1층 출구 → K동 1층 통과 → R동 1층 입구").contains("길 2: 걸어서 약 6분").contains("비 오면 K동 안으로");
    }

    @Test
    void 별칭으로도_찾고_직선_어림이면_밝힌다() {
        WalkRouteService routes = mock(WalkRouteService.class);
        when(routes.routes(2L, null, 1L, null)).thenReturn(List.of(route(3, 205, true, List.of())));
        StringBuilder text = new StringBuilder();
        assertThat(liveData(routes).walkRoute("K동", "R동", text)).hasSize(2);
        assertThat(text.toString()).contains("직선 거리로 어림");
    }

    @Test
    void 건물이_아니거나_여럿에_맞으면_길을_찾지_않는다() {
        WalkRouteService routes = mock(WalkRouteService.class);
        StringBuilder gate = new StringBuilder();
        assertThat(liveData(routes).walkRoute("정문", "홍문관", gate)).isEmpty();
        assertThat(gate.toString()).contains("정문·후문·제휴업체는 안 된다");

        StringBuilder family = new StringBuilder();
        assertThat(liveData(routes).walkRoute("공학관", "홍문관", family)).isEmpty();
        assertThat(family.toString()).contains("여럿이에요").contains("제4공학관 T동").contains("제1공학관 K동");
        verify(routes, never()).routes(anyLong(), isNull(), anyLong(), isNull());
    }

    @Test
    void 길을_찾은_도착_건물이면_버튼에_출발_건물을_싣는다() {
        AskLiveData.Reference t = new AskLiveData.Reference("building:hongik_t", "제4공학관 T동", null, null);
        AskLiveData.Reference r = new AskLiveData.Reference("building:hongik_r", "홍문관 R동", null, null);
        Map<String, AskLiveData.Reference> references = Map.of(t.ref(), t, r.ref(), r, AskLlmClient.ROUTE_FROM_PREFIX + r.ref(), t);

        // '몇 분 걸려'처럼 mapMode 가 show 여도 길을 찾았으면 길찾기 버튼.
        AskLlmClient.MapTarget map = AskLlmClient.mapTarget("building:hongik_r", false, references);
        assertThat(map.route()).isTrue();
        assertThat(map.from()).isEqualTo(t);
        assertThat(AskLlmClient.mapTarget("building:hongik_t", false, references).from()).isNull();

        List<AskRouteResponse.Action> actions = AskRouteService.withRoute(
                AskRouteService.mapActions(r, null, "제4공학관 T동에서 홍문관 R동까지 걸어서 약 4분이에요."), true, r.ref(), t);
        assertThat(actions).extracting(AskRouteResponse.Action::type, AskRouteResponse.Action::from)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("map", null), org.assertj.core.groups.Tuple.tuple("route", "building:hongik_t"));
        // 교차 확인으로 지도 대상이 바뀌었으면 출발을 싣지 않는다(내 위치 출발).
        assertThat(AskRouteService.withRoute(AskRouteService.mapActions(t, null, "x"), true, r.ref(), t).get(1).from()).isNull();
    }
}
