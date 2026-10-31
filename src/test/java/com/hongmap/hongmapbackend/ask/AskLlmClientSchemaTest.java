package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AskLlmClientSchemaTest {

    @Test
    void 답_섹션_칸은_항상_제목_항목_번호_표_순서다() {
        Map<String, Object> properties = AskLlmClient.sectionProperties(Map.of("type", "null"));

        assertThat(properties.keySet()).containsExactly("title", "items", "ordered", "table");
    }
}
