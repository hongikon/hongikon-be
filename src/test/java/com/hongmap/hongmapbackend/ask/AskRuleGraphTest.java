package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 규정 그래프(docs/ask-rule-graph-plan.md): 불러올 때 검사(4-1), 이웃 찾기·자르기(5절 2~3), 답 쓰기에 붙이기.
 * 카탈로그는 작은 가짜, 그래프는 src/test/resources/ask/rule-graph-test.json. 실제 Claude 는 부르지 않는다.
 */
class AskRuleGraphTest {

    static final String TEST_RESOURCE = "ask/rule-graph-test.json";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static AskCatalog catalog(String version) {
        Map<String, String> sources = Map.of(
                "retake/apply", "학기재수강 안내. 학기재수강을 해도 이미 받은 성적경고는 없어지지 않는다. 재학 중 한 번만 할 수 있고 휴학 중에는 신청하지 않는다.",
                "warning/general", "성적경고 안내. 성적경고를 연속 3회 받으면 제적된다.",
                "expulsion/general", "제적 안내. 제적되면 재입학을 신청한다.",
                "readmit/general", "재입학 안내.",
                "leave/general", "휴학 안내. 휴학한 학기는 연속 성적경고를 끊지 않는다.",
                "scholarship/leave", "장학 안내. 장학생이 학기재수강을 하면 장학금을 돌려낸다.");
        List<AskCatalog.Entry> entries = sources.keySet().stream().sorted()
                .map(id -> new AskCatalog.Entry(id, "answer", id + " 요약")).toList();
        return new AskCatalog(new AskCatalog.CatalogFile(version, entries), new AskCatalog.SourcesFile(version, sources));
    }

    private static AskRuleGraph graph(int maxEdges, int maxGuides) {
        return new AskRuleGraph(catalog("test-v1"), AskRuleGraph.read(MAPPER, TEST_RESOURCE), true, maxEdges, maxGuides);
    }

    private static AskRuleGraph.Edge edge(String id, String guide, String quote, String reviewed) {
        return new AskRuleGraph.Edge(id, "a", "b", "결과", "사실 문장", guide, quote, null, reviewed);
    }

    private static AskRuleGraph.GraphFile file(String catalogVersion, List<AskRuleGraph.Edge> edges) {
        return new AskRuleGraph.GraphFile("t", catalogVersion, List.of(
                new AskRuleGraph.Node("a", "가", List.of("retake/apply")),
                new AskRuleGraph.Node("b", "나", List.of("warning/general"))), edges);
    }

    // ---- 이웃 찾기·자르기 ----

    @Test
    void 일단계는_나가는_모든_선과_들어오는_예외_제한_이단계는_결과_선만_삼단계는_가지_않는다() {
        AskRuleGraph.Related related = graph(8, 2).related(List.of("retake/apply"));
        // 단계 먼저(1 → 2), 같은 단계 안에서는 결과 > 예외 > 제한 > 선행조건 > 기간.
        assertThat(related.edgeIds()).containsExactly("e-step1", "e-in-exception", "e-limit", "e-period", "e-step2");
        assertThat(related.edgeIds()).doesNotContain("e-step3", "e-in-result", "e-step2-other", "e-unreviewed");
    }

    @Test
    void 선은_상한에서_자르고_이웃_안내는_일단계_결과_예외_선의_근거만() {
        AskRuleGraph.Related cut = graph(3, 2).related(List.of("retake/apply"));
        assertThat(cut.edgeIds()).containsExactly("e-step1", "e-in-exception", "e-limit");
        // e-step1 근거는 이미 answerIds 에 있다. 제한 선(e-limit)의 근거는 원문으로 넘기지 않는다.
        assertThat(cut.guides()).containsExactly("scholarship/leave");

        assertThat(graph(8, 0).related(List.of("retake/apply")).guides()).isEmpty();
        assertThat(graph(1, 2).related(List.of("retake/apply")).edgeIds()).containsExactly("e-step1");
    }

    @Test
    void 블록은_선마다_한_줄이고_조건이_있으면_되묻기_기준을_붙인다() {
        String block = graph(8, 2).related(List.of("retake/apply")).block();
        assertThat(block).startsWith("\n\n" + AskRuleGraph.BLOCK_HEAD + "\n");
        assertThat(block).contains("- 학기재수강 ->(결과) 성적경고: 학기재수강을 해도 이미 받은 성적경고는 없어지지 않는다. (근거: retake/apply)");
        assertThat(block).contains("- 장학 ->(예외) 학기재수강: 장학생은 학기재수강을 하면 장학금을 돌려낸다. (근거: scholarship/leave) (조건: 장학생 - 질문에서 알 수 없으면 갈리는 기준을 보이고 묻는다)");
        assertThat(block.split("\n")).hasSize(2 + 1 + 5);
    }

    @Test
    void 출발점이_없거나_꺼져_있으면_빈_결과() {
        assertThat(graph(8, 2).related(List.of("readmit/general")).isEmpty()).isTrue();
        assertThat(graph(8, 2).related(List.of()).isEmpty()).isTrue();
        AskRuleGraph off = new AskRuleGraph(catalog("test-v1"), AskRuleGraph.read(MAPPER, TEST_RESOURCE), false, 8, 2);
        assertThat(off.enabled()).isFalse();
        assertThat(off.related(List.of("retake/apply"))).isEqualTo(AskRuleGraph.Related.EMPTY);
    }

    // ---- 불러올 때 검사(4-1) ----

    @Test
    void reviewed_가_비어_있으면_읽지_않고_버린_선으로도_세지_않는다() {
        AskRuleGraph graph = new AskRuleGraph(catalog("test-v1"), file("test-v1", List.of(
                edge("ok", "retake/apply", null, "2026-10-30 운영진"),
                edge("blank", "retake/apply", null, " "),
                edge("missing", "retake/apply", null, null))), true, 8, 2);
        assertThat(graph.related(List.of("retake/apply")).edgeIds()).containsExactly("ok");
        assertThat(graph.dropped()).isEmpty();
        assertThat(graph.warned()).isFalse();

        AskRuleGraph onlyUnreviewed = new AskRuleGraph(catalog("test-v1"), file("test-v1", List.of(
                edge("blank", "retake/apply", null, ""))), true, 8, 2);
        assertThat(onlyUnreviewed.enabled()).isFalse();
    }

    @Test
    void 카탈로그에_없는_guide_의_선은_버린다() {
        AskRuleGraph graph = new AskRuleGraph(catalog("test-v1"), file("test-v1", List.of(
                edge("ok", "retake/apply", null, "r"),
                edge("gone", "removed/guide", null, "r"),
                edge("none", AskCatalog.NONE, null, "r"))), true, 8, 2);
        assertThat(graph.dropped()).containsExactly("gone", "none");
        assertThat(graph.related(List.of("retake/apply")).edgeIds()).containsExactly("ok");
    }

    @Test
    void 카탈로그_버전이_다르면_quote_가_원문에_글자_그대로_있는_선만_남긴다() {
        List<AskRuleGraph.Edge> edges = List.of(
                edge("exact", "retake/apply", "이미 받은 성적경고는 없어지지 않는다", "r"),
                edge("changed", "retake/apply", "성적경고가 없어진다", "r"),
                edge("no-quote", "retake/apply", null, "r"));
        // 같은 버전이면 quote 를 대조하지 않는다.
        assertThat(new AskRuleGraph(catalog("test-v1"), file("test-v1", edges), true, 8, 2).dropped()).isEmpty();
        // 다른 버전이면 선마다 대조해 맞지 않는 선만 버린다(그래프 전체를 끄지 않는다).
        AskRuleGraph changed = new AskRuleGraph(catalog("test-v2"), file("test-v1", edges), true, 8, 2);
        assertThat(changed.dropped()).containsExactly("changed", "no-quote");
        assertThat(changed.enabled()).isTrue();
        assertThat(changed.related(List.of("retake/apply")).edgeIds()).containsExactly("exact");
    }

    @Test
    void 버린_선이_20퍼센트를_넘으면_경고한다() {
        List<AskRuleGraph.Edge> five = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            five.add(edge("ok" + i, "retake/apply", null, "r"));
        }
        five.add(edge("gone", "removed/guide", null, "r"));
        // 5개 중 1개(20%)는 경고 아님, 2개(40%)는 경고.
        assertThat(new AskRuleGraph(catalog("test-v1"), file("test-v1", five), true, 8, 2).warned()).isFalse();
        List<AskRuleGraph.Edge> more = new ArrayList<>(five);
        more.set(0, edge("gone2", "removed/guide", null, "r"));
        AskRuleGraph graph = new AskRuleGraph(catalog("test-v1"), file("test-v1", more), true, 8, 2);
        assertThat(graph.dropped()).containsExactly("gone2", "gone");
        assertThat(graph.warned()).isTrue();
    }

    @Test
    void 이웃_찾기에_필요한_칸이_틀린_선도_버린다() {
        AskRuleGraph graph = new AskRuleGraph(catalog("test-v1"), file("test-v1", List.of(
                new AskRuleGraph.Edge("bad-type", "a", "b", "원인", "사실", "retake/apply", null, null, "r"),
                new AskRuleGraph.Edge("bad-node", "a", "zzz", "결과", "사실", "retake/apply", null, null, "r"),
                new AskRuleGraph.Edge("bad-cond", "a", "b", "결과", "사실", "retake/apply", null, Map.of("성별", true), "r"),
                new AskRuleGraph.Edge(null, "a", "b", "예외", "사실", "retake/apply", null, Map.of("휴학중", true), "r"))),
                true, 8, 2);
        assertThat(graph.dropped()).containsExactly("bad-type", "bad-node", "bad-cond");
        // id 칸이 없으면 '시작점>끝점:종류'.
        assertThat(graph.related(List.of("retake/apply")).edgeIds()).containsExactly("a>b:예외");
    }

    @Test
    void 파일이_없거나_저장소_기본_파일이_비어_있으면_꺼진다() throws Exception {
        assertThat(AskRuleGraph.read(MAPPER, "ask/no-such-graph.json")).isNull();
        assertThat(new AskRuleGraph(catalog("test-v1"), (AskRuleGraph.GraphFile) null, true, 8, 2).enabled()).isFalse();
        // 저장소의 rule-graph.json 은 아직 선이 없다(프론트 검토 뒤에 넣는다) — 켜도 지금 동작 그대로.
        AskCatalog real = new AskCatalog(MAPPER);
        AskRuleGraph.GraphFile shipped = AskRuleGraph.read(MAPPER, AskRuleGraph.RESOURCE);
        assertThat(shipped).isNotNull();
        assertThat(shipped.catalogVersion()).isEqualTo(real.version());
        assertThat(new AskRuleGraph(real, MAPPER, true, 8, 2).enabled()).isFalse();
    }

    // ---- 답 쓰기 지시 ----

    @Test
    void 답_쓰기_지시는_그래프가_켜져_있을_때만_붙는다() throws Exception {
        AskCatalog real = new AskCatalog(MAPPER);
        AskRetriever retriever = new AskRetriever(new AskRetrieverTest.FakeEmbed(Map.of()), null, false);
        AskLlmClient off = new AskLlmClient(real, null, retriever, new AskRuleGraph(real, (AskRuleGraph.GraphFile) null, false, 8, 2), MAPPER,
                "", "claude-haiku-5-5", 20, "5m");
        assertThat(off.answerSystem()).isEqualTo(AskLlmClient.ANSWER_SYSTEM);
        AskLlmClient on = new AskLlmClient(real, null, retriever, graph(8, 2), MAPPER, "", "claude-haiku-5-5", 20, "5m");
        assertThat(on.answerSystem()).isEqualTo(AskLlmClient.ANSWER_SYSTEM + AskLlmClient.RULE_GRAPH_SYSTEM);
        assertThat(AskLlmClient.RULE_GRAPH_SYSTEM).contains("[함께 볼 규정]은 보조다");
    }

    // ---- AskRouteService 에 끼우기 ----

    private record Routed(List<String> answerIds, String context, AskTrace trace, AskRouteService service) {
    }

    private static final AskRouteRequest REQUEST = new AskRouteRequest("학기재수강 하면 경고 없어져?", null, null);

    private static Routed route(AskRuleGraph graph) {
        return route(graph, REQUEST);
    }

    private static Routed route(AskRuleGraph graph, AskRouteRequest request) {
        AskCatalog catalog = catalog("test-v1");
        AskLlmClient llm = mock(AskLlmClient.class);
        when(llm.classify(anyString(), any())).thenReturn(new AskLlmClient.Classification(AskScope.SCHOOL, List.of("retake/apply")));
        when(llm.answer(anyList(), anyString(), any())).thenReturn(new AskLlmClient.Answer("답", true, List.of()));
        AskRetriever retriever = mock(AskRetriever.class);
        AskRouteService service = new AskRouteService(catalog, llm, mock(AskLiveData.class), retriever, graph,
                Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), ZoneOffset.UTC));
        AskTrace trace = new AskTrace(AskProgress.NONE);
        service.route(request, trace);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> ids = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(llm).answer(ids.capture(), context.capture(), any());
        return new Routed(List.copyOf(ids.getValue()), context.getValue(), trace, service);
    }

    @Test
    void 꺼져_있거나_파일이_없으면_context_와_answerIds_가_지금과_같다() {
        for (AskRuleGraph graph : List.of(
                new AskRuleGraph(catalog("test-v1"), AskRuleGraph.read(MAPPER, TEST_RESOURCE), false, 8, 2),
                new AskRuleGraph(catalog("test-v1"), AskRuleGraph.read(MAPPER, "ask/no-such-graph.json"), true, 8, 2))) {
            Routed routed = route(graph);
            assertThat(routed.answerIds()).containsExactly("retake/apply");
            String expected = routed.service().answerContext(REQUEST)
                    + routed.service().missingContext(REQUEST, List.of("retake/apply"), AskProgress.NONE);
            assertThat(routed.context()).isEqualTo(expected).doesNotContain("[함께 볼 규정]");
            assertThat(routed.trace().steps()).noneMatch(step -> step.startsWith("규정 그래프"));
        }
    }

    @Test
    void 켜져_있으면_context_끝에_블록이_붙고_이웃_안내가_answerIds_뒤에_더해진다() {
        AskRuleGraph graph = graph(8, 2);
        Routed routed = route(graph);
        // 질문에 '장학생' 상황이 없으니 장학생 조건 선(e-in-exception)과 그 이웃 안내는 넘기지 않는다(10-31 평가).
        assertThat(routed.answerIds()).containsExactly("retake/apply");
        assertThat(routed.context()).endsWith(graph.related(List.of("retake/apply"), REQUEST.question()).block());
        assertThat(routed.context()).doesNotContain("장학생은 학기재수강");
        assertThat(routed.context()).startsWith(routed.service().answerContext(REQUEST));
        // 쓴 선 id 는 기록 steps 에 한 줄로(질문 글은 넣지 않는다), 시간 줄은 그 뒤 맨 끝.
        assertThat(routed.trace().steps())
                .anyMatch(step -> step.startsWith("규정 그래프: e-step1") && !step.contains("e-in-exception"));
        List<String> recorded = routed.trace().steps(10);
        assertThat(recorded.get(recorded.size() - 1)).startsWith(AskTiming.PREFIX);
    }

    @Test
    void 질문에_조건_상황이_있으면_그_조건_선과_이웃_안내를_넘긴다() {
        AskRuleGraph graph = graph(8, 2);
        AskRouteRequest scholar = new AskRouteRequest("장학생인데 학기재수강 하면 경고 없어져?", null, null);
        Routed routed = route(graph, scholar);
        assertThat(routed.answerIds()).containsExactly("retake/apply", "scholarship/leave");
        assertThat(routed.context()).contains("장학생은 학기재수강을 하면 장학금을 돌려낸다.").contains("학생이 말한 상황");
    }
}
