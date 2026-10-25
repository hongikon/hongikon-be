package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 답 섹션 상한 — 휴대폰에서 한눈에 읽히게 서버가 자른다(10-25 '전과 방법' 답이 섹션 3개·항목 11개로 길었다). */
class AskAnswerSectionsTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void 섹션은_3개_항목은_합쳐_6개까지만_남긴다() {
        String json = """
                [
                  {"title": "지원 자격", "items": ["a1", "a2", "a3", "a4"], "ordered": false, "table": null},
                  {"title": "절차", "items": ["b1", "b2", "b3", "b4"], "ordered": true, "table": null},
                  {"title": "꼭 확인할 점", "items": ["c1", "c2", "c3"], "ordered": false, "table": null},
                  {"title": "더", "items": ["d1"], "ordered": false, "table": null}
                ]
                """;
        List<AskRouteResponse.Section> sections = AskLlmClient.sections(jsonMapper.readTree(json));

        assertThat(sections).extracting(AskRouteResponse.Section::title).containsExactly("지원 자격", "절차");
        assertThat(sections.stream().mapToInt(s -> s.items().size()).sum()).isEqualTo(AskLlmClient.MAX_TOTAL_ITEMS);
        assertThat(sections.get(1).items()).containsExactly("b1", "b2");
    }

    @Test
    void 꼭_확인할_점은_2개까지만_남긴다() {
        String json = """
                [
                  {"title": "신청 방법", "items": ["a1", "a2"], "ordered": true, "table": null},
                  {"title": "꼭 확인할 점", "items": ["c1", "c2", "c3", "c4"], "ordered": false, "table": null}
                ]
                """;
        List<AskRouteResponse.Section> sections = AskLlmClient.sections(jsonMapper.readTree(json));

        assertThat(sections.get(1).items()).containsExactly("c1", "c2");
    }

    @Test
    void 항목이_다_찬_뒤에도_표는_남긴다() {
        String json = """
                [
                  {"title": "목록", "items": ["1", "2", "3", "4", "5"], "ordered": false, "table": null},
                  {"title": "목록2", "items": ["6"], "ordered": false, "table": null},
                  {"title": "반환", "items": [], "ordered": false, "table": {"head": ["기간", "반환"], "rows": [["30일", "5/6"]]}}
                ]
                """;
        List<AskRouteResponse.Section> sections = AskLlmClient.sections(jsonMapper.readTree(json));

        assertThat(sections).hasSize(3);
        assertThat(sections.get(2).table().rows()).containsExactly(List.of("30일", "5/6"));
    }
}
