package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AskCatalogTest {

    @Test
    void 저장소의_카탈로그를_읽고_none을_마지막_선택지로_붙인다() throws Exception {
        AskCatalog catalog = new AskCatalog(JsonMapper.builder().build());

        assertThat(catalog.ids()).hasSizeGreaterThan(100).last().isEqualTo(AskCatalog.NONE);
        assertThat(catalog.contains("leave-of-absence/start")).isTrue();
        assertThat(catalog.contains("없는/노드")).isFalse();
        assertThat(catalog.version()).isNotBlank();
        assertThat(catalog.systemPrompt()).contains("leave-of-absence/start: ").endsWith("학교 생활·행정과 무관한 질문\n");
    }

    @Test
    void 시스템_프롬프트는_같은_입력이면_바이트까지_같다() throws Exception {
        // 프롬프트 캐시 앞부분 — 기동마다 달라지면 캐시를 못 읽는다.
        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(new AskCatalog(mapper).systemPrompt()).isEqualTo(new AskCatalog(mapper).systemPrompt());
    }

    @Test
    void 겹치는_id나_none은_받지_않는다() {
        AskCatalog.Entry entry = new AskCatalog.Entry("a/start", "answer", "요약");
        assertThatThrownBy(() -> new AskCatalog(new AskCatalog.CatalogFile("v", List.of(entry, entry)), new AskCatalog.SourcesFile("v", Map.of())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AskCatalog(new AskCatalog.CatalogFile("v",
                List.of(new AskCatalog.Entry(AskCatalog.NONE, "answer", "요약"))), new AskCatalog.SourcesFile("v", Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 원문이_있고_되묻기_단계와_낱말_검색을_구분한다() throws Exception {
        AskCatalog catalog = new AskCatalog(JsonMapper.builder().build());

        assertThat(catalog.source("leave-of-absence/tuition")).contains("학기 시작(3월 1일·9월 1일) 전이면 전액");
        assertThat(catalog.isQuestion("leave-of-absence/start")).isTrue();
        assertThat(catalog.isQuestion("leave-of-absence/tuition")).isFalse();
        assertThat(catalog.search("휴학 등록금 반환", 6)).contains("leave-of-absence/tuition").hasSizeLessThanOrEqualTo(6);
        assertThat(catalog.search("가", 6)).isEmpty();
    }

    @Test
    void 같은_흐름과_같은_되묻기_묶음의_안내를_이웃으로_찾는다() {
        AskCatalog catalog = new AskCatalog(new AskCatalog.CatalogFile("v", List.of(
                new AskCatalog.Entry("leave/start", "question", "휴학", List.of("leave/apply", "return/start")),
                new AskCatalog.Entry("leave/apply", "answer", "휴학 신청"),
                new AskCatalog.Entry("leave/tuition", "answer", "휴학 등록금"),
                new AskCatalog.Entry("return/start", "answer", "복학"),
                new AskCatalog.Entry("seasonal/start", "answer", "계절학기"))), new AskCatalog.SourcesFile("v", Map.of()));

        assertThat(catalog.family("leave/apply")).containsExactlyInAnyOrder("leave/tuition", "return/start");
        assertThat(catalog.family("seasonal/start")).isEmpty();
        assertThat(catalog.family("없는/안내")).isEmpty();
    }

    @Test
    void 두_파일의_버전이_다르면_기동하지_않는다() {
        assertThatThrownBy(() -> new AskCatalog(new AskCatalog.CatalogFile("a", List.of()), new AskCatalog.SourcesFile("b", Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 분류_프롬프트는_10만_토큰_단가_구간_아래에_머문다() throws Exception {
        // 요청이 10만 토큰을 넘으면 Haiku 단가가 5배가 된다. 한국어 카탈로그는 글자당 약 0.84토큰(10-16, 731노드 9.35만 자 ≈ 7.84만 토큰).
        // 이 테스트가 깨지면 export-catalog.mjs 의 TEXT_MAX·WORDS_MAX 를 줄이거나 노드를 나눠 고르는 방식을 검토한다.
        AskCatalog catalog = new AskCatalog(JsonMapper.builder().build());
        int enumChars = catalog.ids().stream().mapToInt(id -> id.length() + 3).sum();
        assertThat(catalog.systemPrompt().length() + enumChars).isLessThan(110_000);
    }
}
