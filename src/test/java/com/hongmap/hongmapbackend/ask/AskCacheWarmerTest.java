package com.hongmap.hongmapbackend.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class AskCacheWarmerTest {

    private static AskLlmClient client(String apiKey, String ttl) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        AskCatalog catalog = new AskCatalog(mapper);
        AskRetriever retriever = new AskRetriever(new AskRetrieverTest.FakeEmbed(Map.of()), null, false);
        AskRuleGraph graphOff = new AskRuleGraph(catalog, (AskRuleGraph.GraphFile) null, false, 8, 2);
        return new AskLlmClient(catalog, null, retriever, graphOff, mapper, apiKey, "claude-haiku-5-5", 20, ttl);
    }

    @Test
    void 서비스_시간_안에서만_데운다() throws Exception {
        AskCacheWarmer warmer = new AskCacheWarmer(client("", "1h"), true, "7-24", 50);
        assertThat(warmer.inHours(LocalTime.of(6, 59))).isFalse();
        assertThat(warmer.inHours(LocalTime.of(7, 0))).isTrue();
        assertThat(warmer.inHours(LocalTime.of(23, 59))).isTrue();
        assertThat(warmer.inHours(LocalTime.of(0, 30))).isFalse();
    }

    @Test
    void 설정이_잘못되면_기동에서_실패한다() throws Exception {
        AskLlmClient off = client("", "1h");
        assertThatThrownBy(() -> new AskCacheWarmer(off, true, "24-7", 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AskCacheWarmer(off, true, "7", 50)).isInstanceOf(IllegalArgumentException.class);
        // 1시간 캐시가 끝나기 전에 데우려면 확인 간격(5분)만큼 여유가 있어야 한다.
        assertThatThrownBy(() -> new AskCacheWarmer(off, true, "7-24", 58)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 키가_없거나_5분_캐시면_데우지_않는다() throws Exception {
        assertThat(client("", "1h").warmIfIdle(Duration.ZERO)).isFalse();
        // 5분 캐시는 1시간마다 데워도 소용없다 — 키가 있어도 호출하지 않는다(호출하면 네트워크 오류로 false 가 아니라 여기서 바로 false).
        assertThat(client("test-key", "5m").warmIfIdle(Duration.ZERO)).isFalse();
    }

    @Test
    void 토큰_줄에_캐시_쓰기를_따로_남긴다() throws Exception {
        AskUsage usage = AskUsage.ZERO.plus(8_000, 1_500, 85_000, 9_000).plus(2_000, 500, 94_000, 0);
        // input 은 지금처럼 캐시 쓰기를 포함한 값(ask_conversation_logs.input_tokens).
        assertThat(usage.input()).isEqualTo(19_000);
        assertThat(usage.cacheWrite()).isEqualTo(9_000);
        assertThat(usage.line()).isEqualTo("토큰: 입력 10000 · 캐시 쓰기 9000 · 캐시 읽기 179000 · 출력 2000");
    }
}
