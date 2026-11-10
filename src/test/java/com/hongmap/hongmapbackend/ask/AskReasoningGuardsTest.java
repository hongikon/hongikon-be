package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 11-10 Haiku 평가에서 나온 손해 오류를 서버가 막는 장치 — 날짜 단정·조건 선 잘림·결과 누락. */
class AskReasoningGuardsTest {

    @Test
    void 시점을_말한_질문만_날짜를_받는다() {
        assertThat(AskSituation.mentionsTime("그럼 그거 언제까지야?")).isTrue();
        assertThat(AskSituation.mentionsTime("지금 T동 열람실 열려 있어?")).isTrue();
        assertThat(AskSituation.mentionsTime("이번 학기 휴학 신청 아직 돼?")).isTrue();
        assertThat(AskSituation.mentionsTime("2학년부터 다시 다니고 싶어")).isFalse();
        assertThat(AskSituation.mentionsTime("2학년 10월에 입대하는데 수강신청은 어떻게 해?")).isFalse();
    }

    @Test
    void 학생이_말한_상황의_조건_선이_결과_선보다_먼저_남는다() {
        AskRuleGraph.Edge result1 = new AskRuleGraph.Edge("r1", "a", "b", "결과", "결과 하나", "warning/general", null, null, "ok");
        AskRuleGraph.Edge result2 = new AskRuleGraph.Edge("r2", "a", "c", "결과", "결과 둘", "warning/general", null, null, "ok");
        AskRuleGraph.Edge deferral = new AskRuleGraph.Edge("d", "a", "c", "제한", "졸업유예생은 재수강 안 된다", "warning/general", null,
                Map.of("졸업유예", true), "ok");
        AskRuleGraph graph = new AskRuleGraph(AskRuleGraphTest.catalog("t"), new AskRuleGraph.GraphFile("t", "t", List.of(
                new AskRuleGraph.Node("a", "재수강", List.of("retake/apply")),
                new AskRuleGraph.Node("b", "성적경고", List.of("warning/general")),
                new AskRuleGraph.Node("c", "졸업유예", List.of("expulsion/general"))), List.of(result1, result2, deferral)), true, 2, 1);
        assertThat(graph.related(List.of("retake/apply"), "졸업유예생인데 재수강 돼?").edgeIds()).containsExactly("d", "r1");
        // 상황을 말하지 않으면 조건 선은 빠지고 결과 선만.
        assertThat(graph.related(List.of("retake/apply"), "재수강 돼?").edgeIds()).containsExactly("r1", "r2");
        assertThat(graph.related(List.of("retake/apply"), "재수강 돼?").consequences())
                .extracting(AskRuleGraph.Consequence::target).containsExactly("성적경고", "졸업유예");
    }

    @Test
    void 답에_빠진_결과만_꼭_확인할_점에_서버가_붙인다() {
        List<AskRuleGraph.Consequence> consequences = List.of(
                new AskRuleGraph.Consequence("성적경고", "학기재수강을 해도 성적경고 기록은 남는다."),
                new AskRuleGraph.Consequence("재학연한", "지운 학기도 재학연한에 들어간다."),
                new AskRuleGraph.Consequence("등록금", "졸업이 늦어져 등록금을 더 낼 수 있다."));
        List<AskRouteResponse.Section> sections = List.of(new AskRouteResponse.Section("신청 방법", List.of("클래스넷에서 신청"), true, null));

        List<AskRouteResponse.Section> out = AskRouteService.fillConsequences(sections, "학기재수강은 되지만 성적 경고는 그대로예요.", consequences);

        assertThat(out).hasSize(2);
        assertThat(out.get(1).title()).isEqualTo(AskLlmClient.CAUTION_TITLE);
        assertThat(out.get(1).items()).containsExactly("지운 학기도 재학연한에 들어간다.", "졸업이 늦어져 등록금을 더 낼 수 있다.");
        // 이미 다 들어 있으면 그대로.
        assertThat(AskRouteService.fillConsequences(sections, "성적경고·재학연한·등록금 모두 확인", consequences)).isEqualTo(sections);
        // 꼭 확인할 점이 있으면 그 섹션 뒤에 잇는다.
        List<AskRouteResponse.Section> withCaution = List.of(new AskRouteResponse.Section(AskLlmClient.CAUTION_TITLE, List.of("기존"), false, null));
        assertThat(AskRouteService.fillConsequences(withCaution, "성적경고, 등록금", consequences).get(0).items())
                .containsExactly("기존", "지운 학기도 재학연한에 들어간다.");
    }

    @Test
    void 다른_말로_이미_한_결과는_다시_붙이지_않는다() {
        String said = "지운학기를빼고8학기를채워야해서졸업이늦어지고등록금을더낼수있어요";
        assertThat(AskRouteService.alreadySaid(said, "학기재수강으로 지운 학기를 빼고 8학기(5년제 10학기)를 채워야 해서 졸업이 늦어지고 등록금을 더 낼 수 있다.")).isTrue();
        assertThat(AskRouteService.alreadySaid(said, "학기재수강으로 지운 학기도 재학연한에 들어간다.")).isFalse();
    }
}
