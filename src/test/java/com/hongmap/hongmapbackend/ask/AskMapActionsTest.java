package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** '지도에서 보기' 버튼(11-07): 모델이 고른 장소, 없으면 안내 위치. 장소를 물을 때만(11-09). */
class AskMapActionsTest {

    private static AskLiveData.Reference ref(String ref, String title) {
        return new AskLiveData.Reference(ref, title, null, null);
    }

    @Test
    void chosenPlaceBecomesTheMapAction() {
        List<AskRouteResponse.Action> actions = AskRouteService.mapActions(ref("building:hongik_t", "제4공학관 T동"),
                "f1", "T동은 정문에서 왼쪽 언덕 위에 있어요. 6층에 멀티미디어실이 있어요.");
        assertThat(actions).containsExactly(new AskRouteResponse.Action("map", "building:hongik_t", "제4공학관 T동",
                "T동은 정문에서 왼쪽 언덕 위에 있어요."));
    }

    @Test
    void facilityLabelIsLeftToTheAppAndGuideLocationIsTheFallback() {
        assertThat(AskRouteService.mapActions(ref("facility:f9", "멀티미디어실 · 6층 · 09:00~22:00"), null, "답"))
                .extracting(AskRouteResponse.Action::target, AskRouteResponse.Action::label)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("facility:f9", null));
        assertThat(AskRouteService.mapActions(null, "f1", null))
                .containsExactly(new AskRouteResponse.Action("map", "facility:f1", null, null));
        assertThat(AskRouteService.mapActions(ref("building:", "빈 id"), null, "답")).isEmpty();
    }

    /** 11-09 운영진: 장소를 물을 때만 지도 — 모델이 비워 두면 없음, 도구로 찾지 않은 참조(지어낸 id)도 없음. */
    @Test
    void mapTargetMustBeAPlaceFoundThisTurn() {
        java.util.Map<String, AskLiveData.Reference> found = java.util.Map.of(
                "facility:q-printer", ref("facility:q-printer", "정보통신센터 Q동 1층 · [프린터]"),
                "notice:3", ref("notice:3", "공지"));
        assertThat(AskLlmClient.mapTarget("", found)).isNull();
        assertThat(AskLlmClient.mapTarget("facility:made-up", found)).isNull();
        assertThat(AskLlmClient.mapTarget("notice:3", found)).isNull();
        assertThat(AskLlmClient.mapTarget("facility:q-printer", found).place().ref()).isEqualTo("facility:q-printer");
        assertThat(AskLlmClient.mapTarget("guide", found).place()).isNull();
    }

    @Test
    void summaryIsTheFirstSentenceWithoutMarkdownAndCapped() {
        assertThat(AskRouteService.summaryOf("**학생회관**은 3층이에요.\n- 운영 09:00")).isEqualTo("학생회관은 3층이에요.");
        assertThat(AskRouteService.summaryOf("버전 1.5 안내예요")).isEqualTo("버전 1.5 안내예요");
        assertThat(AskRouteService.summaryOf("가".repeat(200))).hasSize(AskRouteService.ACTION_SUMMARY_MAX);
    }
}
