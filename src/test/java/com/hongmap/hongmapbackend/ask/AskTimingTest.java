package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 단계별 시간 한 줄(기록 steps '시간:') — 거친 단계만 쓰고, 기존 과정 줄 뒤에 붙고, 가림 규칙에 걸리지 않는다. */
class AskTimingTest {

    @Test
    void 모든_단계를_거치면_순서대로_한_줄() {
        AskTiming timing = new AskTiming();
        timing.retrieval(120, 35);
        timing.classify(2100);
        timing.answer(2, 1800, 5400, 8200);
        assertThat(timing.line(10500)).isEqualTo(
                "시간: 임베딩 120ms · 색인 35ms · 고르기 2100ms · 도구 2회 1800ms · 첫 글자 5400ms · 답 쓰기 8200ms · 전체 10500ms");
    }

    @Test
    void RAG가_꺼져_있으면_임베딩과_색인은_뺀다() {
        AskTiming timing = new AskTiming();
        timing.classify(900);
        timing.answer(0, 0, 3000, 4000);
        assertThat(timing.line(5000)).isEqualTo("시간: 고르기 900ms · 도구 0회 0ms · 첫 글자 3000ms · 답 쓰기 4000ms · 전체 5000ms");
    }

    @Test
    void 거절이나_실패로_답_쓰기를_안_했으면_그때까지만() {
        AskTiming refused = new AskTiming();
        refused.classify(1500);
        assertThat(refused.line(1600)).isEqualTo("시간: 고르기 1500ms · 전체 1600ms");

        assertThat(new AskTiming().line(7)).isEqualTo("시간: 전체 7ms");
    }

    @Test
    void 글자가_나오기_전에_끝났으면_첫_글자는_뺀다() {
        AskTiming timing = new AskTiming();
        timing.answer(1, 300, -1, 20000);
        assertThat(timing.line(21000)).isEqualTo("시간: 도구 1회 300ms · 답 쓰기 20000ms · 전체 21000ms");
    }

    @Test
    void 기록용_과정은_기존_줄_뒤에_시간_줄을_붙이고_원래_목록은_그대로() {
        AskTrace trace = new AskTrace(AskProgress.NONE);
        trace.retrieved(List.of("leave/start"));
        trace.retrievalTimed(80, 12);
        trace.classifyTimed(1000);
        trace.picked(List.of("leave/start"));
        trace.answerTimed(0, 0, 2500, 3000);

        List<String> steps = trace.steps(4200);
        assertThat(steps).containsExactly(
                "검색 후보: leave/start",
                "고름: leave/start",
                "시간: 임베딩 80ms · 색인 12ms · 고르기 1000ms · 도구 0회 0ms · 첫 글자 2500ms · 답 쓰기 3000ms · 전체 4200ms");
        assertThat(trace.steps()).hasSize(2);
    }

    @Test
    void 시간_줄은_개인정보_가림에_걸리지_않는다() {
        AskTiming timing = new AskTiming();
        timing.retrieval(1234567, 1010);
        timing.classify(99999);
        timing.answer(6, 1012345, 1067890, 1099999);
        String line = timing.line(1199999);
        assertThat(AskPrivacy.mask(line)).isEqualTo(line);
    }
}
