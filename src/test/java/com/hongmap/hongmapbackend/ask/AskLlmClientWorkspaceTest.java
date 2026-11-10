package com.hongmap.hongmapbackend.ask;

import com.anthropic.client.AnthropicClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** ask.llm.workspace-id 가 있으면 anthropic-workspace-id 헤더를 붙이고, 비어 있으면 붙이지 않는다. */
class AskLlmClientWorkspaceTest {

    private static final String TEST_WORKSPACE = "test-workspace";

    private HttpServer server;
    private final List<String> seenWorkspaceHeaders = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String value = exchange.getRequestHeaders().getFirst(AskLlmClient.WORKSPACE_HEADER);
            seenWorkspaceHeaders.add(value == null ? "<none>" : value);
            byte[] body = "{\"type\":\"error\",\"error\":{\"type\":\"not_found_error\",\"message\":\"test\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String sendOnce(String workspaceId) {
        AnthropicClient client = AskLlmClient.clientBuilder("test-key", 5, workspaceId).baseUrl(baseUrl()).build();
        try {
            // 404 라 예외로 끝난다. 보낸 헤더만 본다.
            catchThrowable(() -> client.models().list());
        } finally {
            client.close();
        }
        assertThat(seenWorkspaceHeaders).isNotEmpty();
        return seenWorkspaceHeaders.get(0);
    }

    @Test
    void 워크스페이스_ID_가_있으면_헤더를_붙인다() {
        assertThat(sendOnce(TEST_WORKSPACE)).isEqualTo(TEST_WORKSPACE);
    }

    @Test
    void 워크스페이스_ID_가_비어_있으면_헤더를_붙이지_않는다() {
        assertThat(sendOnce("")).isEqualTo("<none>");
    }

    @Test
    void 워크스페이스_ID_가_null_이거나_공백이어도_헤더를_붙이지_않는다() {
        assertThat(sendOnce("   ")).isEqualTo("<none>");
        seenWorkspaceHeaders.clear();
        assertThat(sendOnce(null)).isEqualTo("<none>");
    }

    @Test
    void 워크스페이스_ID_가_있든_없든_클라이언트가_만들어진다() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        AskCatalog catalog = new AskCatalog(mapper);
        AskRetriever retriever = new AskRetriever(new AskRetrieverTest.FakeEmbed(Map.of()), null, false);
        AskRuleGraph graphOff = new AskRuleGraph(catalog, (AskRuleGraph.GraphFile) null, false, 8, 2);

        AskLlmClient with = new AskLlmClient(catalog, null, retriever, graphOff, mapper,
                "test-key", "claude-haiku-5-5", 20, "1h", "low", 6000, "", TEST_WORKSPACE);
        AskLlmClient without = new AskLlmClient(catalog, null, retriever, graphOff, mapper,
                "test-key", "claude-haiku-5-5", 20, "1h", "low", 6000, "", "");
        AskLlmClient noKey = new AskLlmClient(catalog, null, retriever, graphOff, mapper,
                "", "claude-haiku-5-5", 20, "1h", "low", 6000, "", TEST_WORKSPACE);

        assertThat(with.enabled()).isTrue();
        assertThat(without.enabled()).isTrue();
        // 키가 없으면 워크스페이스 ID 와 관계없이 예전처럼 꺼진다.
        assertThat(noKey.enabled()).isFalse();
    }
}
