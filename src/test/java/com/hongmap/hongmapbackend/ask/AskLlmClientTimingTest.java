package com.hongmap.hongmapbackend.ask;

import com.anthropic.models.messages.CacheControlEphemeral;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 카탈로그 캐시 유지 시간 설정(ask.llm.cache-ttl)과, 호출이 실패해도 그때까지 잰 시간을 알리는지.
 * 실제 Claude 는 부르지 않는다 — 키를 비워 호출이 바로 실패하게 한다.
 */
class AskLlmClientTimingTest {

    @Test
    void 기본_5분이면_지금과_같은_캐시_지점() {
        CacheControlEphemeral current = CacheControlEphemeral.builder().build();
        assertThat(AskLlmClient.cacheControl("5m")).isEqualTo(current);
        assertThat(AskLlmClient.cacheControl("")).isEqualTo(current);
        assertThat(AskLlmClient.cacheControl(null)).isEqualTo(current);
        assertThat(AskLlmClient.cacheControl("5m").ttl()).isEmpty();
    }

    @Test
    void 한_시간이면_ttl_1h() {
        assertThat(AskLlmClient.cacheControl("1h").ttl()).contains(CacheControlEphemeral.Ttl.TTL_1H);
        assertThat(AskLlmClient.cacheControl(" 1h ").ttl()).contains(CacheControlEphemeral.Ttl.TTL_1H);
    }

    @Test
    void 다른_값이면_기동에서_실패한다() {
        assertThatThrownBy(() -> AskLlmClient.cacheControl("30m")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AskLlmClient.cacheControl("1H")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 호출이_실패해도_고르기와_답_쓰기_시간을_알린다() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        AskCatalog catalog = new AskCatalog(mapper);
        AskRetriever retriever = new AskRetriever(new AskRetrieverTest.FakeEmbed(Map.of()), null, false);
        AskLlmClient client = new AskLlmClient(catalog, null, retriever, mapper, "", "claude-haiku-5-5", 20, "5m");
        List<String> events = new ArrayList<>();
        AskProgress progress = new AskProgress() {
            @Override
            public void classifyTimed(long ms) {
                events.add("classify " + (ms >= 0));
            }

            @Override
            public void answerTimed(int toolRounds, long toolMs, long firstTextMs, long totalMs) {
                events.add("answer " + toolRounds + " " + toolMs + " " + firstTextMs + " " + (totalMs >= 0));
            }
        };

        assertThatThrownBy(() -> client.classify("휴학", progress)).isInstanceOf(AskUnavailableException.class);
        assertThatThrownBy(() -> client.answer(List.of("leave-of-absence/start"), "[이번 질문]\n휴학", progress))
                .isInstanceOf(AskUnavailableException.class);

        assertThat(events).containsExactly("classify true", "answer 0 0 -1 true");
    }
}
