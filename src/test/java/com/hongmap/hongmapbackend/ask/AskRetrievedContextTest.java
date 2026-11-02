package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.hongmap.hongmapbackend.ask.dto.AskRouteRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * RAG 검색 후보 중 고르지 않은 안내를 답 쓰기에 넘기는 블록(11-02 '기숙사 방배정' — 답이 후보에만 있었다).
 */
class AskRetrievedContextTest {

    private static final AskCatalog CATALOG = catalog();

    private static AskCatalog catalog() {
        try {
            return new AskCatalog(JsonMapper.builder().build());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static AskRouteService service() {
        return new AskRouteService(CATALOG, mock(AskLlmClient.class), mock(AskLiveData.class), mock(AskRetriever.class),
                mock(AskRuleGraph.class));
    }

    @Test
    void 고르지_않은_후보만_요약과_함께_넘긴다() {
        String block = service().retrievedContext(List.of("dorm/start", "life-dorm-movein/start", "no-such/guide"), List.of("dorm/start"));
        assertThat(block).startsWith("\n\n[검색 후보 안내]")
                .contains("read_guides")
                .contains("life-dorm-movein/start — " + CATALOG.line("life-dorm-movein/start"))
                .doesNotContain("dorm/start — ")
                .doesNotContain("no-such/guide");
    }

    @Test
    void 후보가_없거나_모두_골랐으면_빈_문자열() {
        assertThat(service().retrievedContext(List.of(), List.of("dorm/start"))).isEmpty();
        assertThat(service().retrievedContext(List.of("dorm/start"), List.of("dorm/start"))).isEmpty();
    }

    @Test
    void 검색_후보가_있으면_앱_낱말_찾기_후보를_앞에_붙이고_없으면_그대로() {
        AskRouteRequest withHint = new AskRouteRequest("기숙사 방배정 어디서 확인해요", "life-dorm-movein/start", null);
        assertThat(service().withHint(withHint, List.of("life-dorm-bring/more")))
                .containsExactly("life-dorm-movein/start", "life-dorm-bring/more");
        assertThat(service().withHint(withHint, List.of())).isEmpty();
        assertThat(service().withHint(withHint, List.of("life-dorm-movein/start"))).containsExactly("life-dorm-movein/start");
        AskRouteRequest noHint = new AskRouteRequest("기숙사 방배정 어디서 확인해요", null, null);
        assertThat(service().withHint(noHint, List.of("life-dorm-bring/more"))).containsExactly("life-dorm-bring/more");
    }

    @Test
    void 고르기_메시지에_검색_후보와_앱_낱말_찾기_후보가_함께_들어간다() {
        AskRouteRequest request = new AskRouteRequest("기숙사 방배정 어디서 확인해요", "life-dorm-movein/start", null);
        assertThat(service().classifyMessage(request, List.of("life-dorm-bring/more")))
                .contains("안내 원문 검색 후보")
                .contains("앱 낱말 찾기 후보: life-dorm-movein/start");
    }
}
