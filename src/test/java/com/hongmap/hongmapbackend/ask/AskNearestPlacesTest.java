package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuService;
import com.hongmap.hongmapbackend.mapdata.MapDataService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 가까운 곳 찾기(11-09 운영진 — 'T동에서 제일 가까운 편의점'). */
class AskNearestPlacesTest {

    private static final String DATA = """
            {"buildings":[
              {"code":"hongik_t","name":"제4공학관 T동","type":"강의동","lat":37.5510,"lng":126.9240},
              {"code":"hongik_g","name":"학생회관 G동","type":"학생 편의","lat":37.5520,"lng":126.9240},
              {"code":"hongik_r","name":"홍문관 R동","type":"강의·행정 복합동","lat":37.5540,"lng":126.9240}],
             "facilities":[
              {"id":"r-store","kind":"편의점","buildingName":"홍문관 R동","floor":-1,"note":"GS25"},
              {"id":"g-store","kind":"편의점","buildingName":"학생회관 G동","floor":1,"note":"CU · 08:00~22:00"},
              {"id":"t-printer","kind":"프린터","buildingName":"제4공학관 T동","floor":3,"note":"흑백"}],
             "partners":[
              {"id":"cafe-near","name":"가까운카페","category":"카페","lat":37.5512,"lng":126.9240,"benefit":"10% 할인"},
              {"id":"cafe-old","name":"끝난카페","category":"카페","lat":37.5511,"lng":126.9240,"endsOn":"2026-01-01"}]}
            """;

    private static AskLiveData liveData() {
        MapDataService map = mock(MapDataService.class);
        when(map.current()).thenReturn(new MapDataService.Snapshot("v", DATA.getBytes(StandardCharsets.UTF_8)));
        CafeteriaMenuService cafeteria = mock(CafeteriaMenuService.class);
        when(cafeteria.today()).thenReturn(LocalDate.of(2026, 11, 9));
        return new AskLiveData(null, cafeteria, map, JsonMapper.builder().build());
    }

    @Test
    void 가까운_순으로_직선거리와_같은_건물을_준다() {
        StringBuilder text = new StringBuilder();
        List<AskLiveData.Reference> hits = liveData().nearestPlaces("T동", "편의점", text);
        assertThat(hits).extracting(AskLiveData.Reference::ref).containsExactly("facility:g-store", "facility:r-store");
        assertThat(text.toString()).contains("출발: 제4공학관 T동").contains("직선 약 110m").contains("직선 약 330m");

        StringBuilder same = new StringBuilder();
        assertThat(liveData().nearestPlaces("T동", "프린터", same)).extracting(AskLiveData.Reference::ref)
                .containsExactly("facility:t-printer");
        assertThat(same.toString()).contains("같은 건물");
    }

    @Test
    void 끝난_제휴는_빼고_모르는_출발지는_찾지_못했다고_한다() {
        StringBuilder text = new StringBuilder();
        assertThat(liveData().nearestPlaces("T동", "카페", text)).extracting(AskLiveData.Reference::ref)
                .containsExactly("partner:cafe-near");
        StringBuilder unknown = new StringBuilder();
        assertThat(liveData().nearestPlaces("정문", "편의점", unknown)).isEmpty();
        assertThat(unknown.toString()).contains("찾지 못했어요");
    }
}
