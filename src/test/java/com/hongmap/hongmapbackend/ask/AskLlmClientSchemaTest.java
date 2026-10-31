package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AskLlmClientSchemaTest {

    @Test
    void 답_섹션_칸은_항상_제목_항목_번호_표_순서다() {
        Map<String, Object> properties = AskLlmClient.sectionProperties(Map.of("type", "null"));

        assertThat(properties.keySet()).containsExactly("title", "items", "ordered", "table");
    }

    @Test
    void 답_쓰기_지시에_캐시_표시를_붙인다() throws Exception {
        // 도구 설명과 답 쓰기 지시는 질문마다 같아 캐시로 읽는다(10-31: 답 쓰기 입력 약 1.78만 토큰 중 약 0.9만이 캐시 읽기로).
        JsonMapper mapper = JsonMapper.builder().build();
        AskCatalog catalog = new AskCatalog(mapper);
        AskRetriever retriever = new AskRetriever(new AskRetrieverTest.FakeEmbed(Map.of()), null, false);
        AskRuleGraph graphOff = new AskRuleGraph(catalog, (AskRuleGraph.GraphFile) null, false, 8, 2);
        AskLlmClient client = new AskLlmClient(catalog, null, retriever, graphOff, mapper, "", "claude-haiku-5-5", 20, "1h");

        assertThat(client.answerSystemBlocks()).hasSize(1);
        assertThat(client.answerSystemBlocks().get(0).text()).isEqualTo(client.answerSystem());
        assertThat(client.answerSystemBlocks().get(0).cacheControl()).isPresent();
    }
}
