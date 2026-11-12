package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuService;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaDayResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaMealResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaRestaurantResponse;
import com.hongmap.hongmapbackend.mapdata.MapDataService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 시설 종류 질문은 그 종류 전부를(11-12 사용자 피드백 — 열람실·프린터·편의점이 일부만 나왔다). */
class AskFacilityKindsTest {

    // ---- 질문 → 종류 ----

    @Test
    void 종류_낱말을_지도_시설_종류로_읽는다() {
        assertThat(AskFacilityKinds.kinds("열람실 어디 있어")).containsExactly("열람실");
        assertThat(AskFacilityKinds.kinds("공부할 수 있는 곳")).containsExactly("열람실", "스터디룸", "라운지");
        assertThat(AskFacilityKinds.kinds("프린트 어디서 해")).containsExactly("프린터");
        assertThat(AskFacilityKinds.kinds("출력 어디서 해?")).containsExactly("프린터");
        assertThat(AskFacilityKinds.kinds("편의점 어디")).containsExactly("편의점");
        assertThat(AskFacilityKinds.kinds("교내 카페 어디 있어")).containsExactly("카페");
        assertThat(AskFacilityKinds.kinds("학교 식당 어디")).containsExactly("식당");
        assertThat(AskFacilityKinds.kinds("스터디룸 예약")).containsExactly("스터디룸");
    }

    @Test
    void 종류_낱말이_없거나_증명서_출력이면_종류가_아니다() {
        assertThat(AskFacilityKinds.kinds("휴학 신청 방법")).isEmpty();
        assertThat(AskFacilityKinds.kinds("증명서 출력 어디서 해")).isEmpty();
        assertThat(AskFacilityKinds.kinds(null)).isEmpty();
    }

    @Test
    void 좁히는_낱말은_건물_시간_낱말만_남기고_조사를_뗀다() {
        assertThat(AskFacilityKinds.narrowingWords("열람실 어디 있어")).isEmpty();
        assertThat(AskFacilityKinds.narrowingWords("K동 열람실")).containsExactly("k동");
        assertThat(AskFacilityKinds.narrowingWords("K동에 열람실 있어?")).containsExactly("k동");
        assertThat(AskFacilityKinds.narrowingWords("중도 프린터")).containsExactly("중앙도서관");
        assertThat(AskFacilityKinds.narrowingWords("24시간 열람실")).containsExactly("24시간");
        assertThat(AskFacilityKinds.narrowingWords("공부할 수 있는 곳")).isEmpty();
    }

    @Test
    void 종류_질문이면_도구를_부르라는_줄을_붙이고_학식_메뉴_질문은_뺀다() {
        assertThat(AskFacilityKinds.contextLine("열람실 어디 있어")).contains("[시설 종류 질문] 열람실").contains("search_facilities");
        assertThat(AskFacilityKinds.contextLine("오늘 학식 뭐야")).isEmpty();
        assertThat(AskFacilityKinds.contextLine("학식 어디서 먹어")).contains("식당");
        assertThat(AskFacilityKinds.contextLine("졸업 요건")).isEmpty();
    }

    // ---- search_facilities ----

    /** 열람실 10곳 + 스터디룸 5곳 + 라운지 3곳 + 프린터 9곳 + 편의점 3곳(맞은 낱말 순 8개 상한을 넘게). */
    private static String data() {
        List<String> rows = new ArrayList<>();
        String[] buildings = {"중앙도서관 H동", "제4공학관 T동", "홍문관 R동", "학생회관 G동", "제1공학관 K동"};
        for (int i = 0; i < 10; i++) {
            rows.add(row("reading-" + i, "열람실", buildings[i % 5], 2 + i, "열람실 " + i));
        }
        for (int i = 0; i < 5; i++) {
            rows.add(row("study-" + i, "스터디룸", buildings[i % 5], 3, "공동학습실 " + i));
        }
        for (int i = 0; i < 3; i++) {
            rows.add(row("lounge-" + i, "라운지", buildings[i % 5], 1, ""));
        }
        for (int i = 0; i < 9; i++) {
            rows.add(row("printer-" + i, "프린터", buildings[i % 5], 1, i == 0 ? "출력센터" : "PC실"));
        }
        rows.add(row("store-r", "편의점", "홍문관 R동", 3, "바이스마트"));
        rows.add(row("store-dorm", "편의점", "제2기숙사", -1, "이마트24 · 24시간"));
        rows.add(row("store-l", "편의점", "와우관 L동", 4, "편의점 · 운영시간 확인 필요"));
        rows.add(row("t-24", "열람실", "제4공학관 T동", 4, "일반·노트북 열람실 · 24시간 개방"));
        rows.add(row("elevator", "엘리베이터", "홍문관 R동", 1, ""));
        return "{\"buildings\":[],\"facilities\":[" + String.join(",", rows) + "]}";
    }

    private static String row(String id, String kind, String building, int floor, String note) {
        return "{\"id\":\"" + id + "\",\"kind\":\"" + kind + "\",\"buildingName\":\"" + building + "\",\"floor\":" + floor
                + ",\"note\":\"" + note + "\"}";
    }

    private static AskLiveData liveData() {
        MapDataService map = mock(MapDataService.class);
        when(map.current()).thenReturn(new MapDataService.Snapshot("v", data().getBytes(StandardCharsets.UTF_8)));
        return new AskLiveData(null, mock(CafeteriaMenuService.class), map, JsonMapper.builder().build());
    }

    @Test
    void 프린트를_물으면_프린터_9곳을_모두_준다() {
        StringBuilder text = new StringBuilder();
        List<AskLiveData.Reference> hits = liveData().searchFacilities("프린트 어디서 해", text);
        assertThat(hits).hasSize(9).allMatch(hit -> hit.ref().startsWith("facility:printer-"));
        assertThat(text).contains("[종류별 전체 목록]").contains("프린터 9곳");
    }

    @Test
    void 공부할_곳은_열람실_스터디룸_라운지를_모두_건물별로_준다() {
        StringBuilder text = new StringBuilder();
        List<AskLiveData.Reference> hits = liveData().searchFacilities("공부할 수 있는 곳", text);
        assertThat(hits).hasSize(11 + 5 + 3);
        // 건물별로 묶였다 — 같은 건물 줄이 끊기지 않고 이어진다.
        List<String> order = new ArrayList<>();
        for (AskLiveData.Reference hit : hits) {
            String building = hit.title().substring(0, hit.title().indexOf(' ', hit.title().indexOf(' ') + 1));
            if (order.isEmpty() || !order.get(order.size() - 1).equals(building)) {
                assertThat(order).doesNotContain(building);
                order.add(building);
            }
        }
    }

    @Test
    void 종류_목록도_상한_25곳에서_자르고_더_있다고_알린다() {
        StringBuilder text = new StringBuilder();
        // 열람실 11 + 스터디룸 5 + 라운지 3 + 프린터 9 = 28
        List<AskLiveData.Reference> hits = liveData().searchFacilities("공부하고 출력할 곳", text);
        assertThat(hits).hasSize(AskLiveData.FACILITY_KIND_LIMIT);
        assertThat(text).contains("3곳 더 있어요");
    }

    @Test
    void 편의점은_세_곳_모두_와우관_포함() {
        StringBuilder text = new StringBuilder();
        List<AskLiveData.Reference> hits = liveData().searchFacilities("편의점 어디", text);
        assertThat(hits).extracting(AskLiveData.Reference::ref)
                .containsExactlyInAnyOrder("facility:store-r", "facility:store-dorm", "facility:store-l");
        assertThat(text).contains("운영시간 확인 필요");
    }

    @Test
    void 건물_낱말이_맞으면_그_건물_것만_없으면_전부() {
        StringBuilder text = new StringBuilder();
        List<AskLiveData.Reference> k = liveData().searchFacilities("K동 열람실", text);
        assertThat(k).extracting(AskLiveData.Reference::ref).containsExactlyInAnyOrder("facility:reading-4", "facility:reading-9");
        assertThat(text).contains("질문 낱말(k동)");

        List<AskLiveData.Reference> h24 = liveData().searchFacilities("24시간 열람실", new StringBuilder());
        assertThat(h24).extracting(AskLiveData.Reference::ref).containsExactly("facility:t-24");

        List<AskLiveData.Reference> none = liveData().searchFacilities("Z동 편의점", new StringBuilder());
        assertThat(none).hasSize(3);
    }

    @Test
    void 종류_낱말이_없으면_예전처럼_맞은_낱말_순_8개() {
        List<AskLiveData.Reference> hits = liveData().searchFacilities("홍문관 R동", new StringBuilder());
        assertThat(hits).hasSizeLessThanOrEqualTo(AskLiveData.FACILITY_SEARCH_LIMIT);
        assertThat(liveData().searchFacilities("엘리베이터", new StringBuilder()))
                .extracting(AskLiveData.Reference::ref).containsExactly("facility:elevator");
    }

    // ---- 문 닫은 곳 ----

    @Test
    void 문_닫은_곳_줄에_파브리카와_교직원식당이_있다() {
        assertThat(AskClosedPlaces.contextLine()).contains("[문 닫은 곳]").contains("파브리카").contains("교직원식당")
                .contains("운영하는 곳으로 쓰지 않는다");
        assertThat(AskClosedPlaces.mentions("교직원식당")).isTrue();
        assertThat(AskClosedPlaces.mentions("학생식당")).isFalse();
    }

    @Test
    void 학식_메뉴에서_메뉴_없는_교직원식당은_뺀다() {
        CafeteriaMenuService cafeteria = mock(CafeteriaMenuService.class);
        LocalDate day = LocalDate.of(2026, 11, 12);
        when(cafeteria.today()).thenReturn(day);
        when(cafeteria.day(any())).thenReturn(new CafeteriaDayResponse(day, "홍익대", "https://www.hongik.ac.kr", null, List.of(
                new CafeteriaRestaurantResponse("dorm2-student", null, "학생식당",
                        List.of(new CafeteriaMealResponse("점심A", "11:30~14:00", "5,800원", List.of("백미밥"), false))),
                new CafeteriaRestaurantResponse("mh-staff", null, "교직원식당", List.of()))));
        AskLiveData.DayMenu menu = new AskLiveData(null, cafeteria, null, null).cafeteria(day, false);
        assertThat(menu.text()).contains("[학생식당]").doesNotContain("교직원식당");
    }
}
