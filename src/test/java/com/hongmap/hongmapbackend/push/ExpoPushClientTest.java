package com.hongmap.hongmapbackend.push;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Expo Push API 요청 형태와 응답(ticket) 파싱. 실제 Expo는 호출하지 않는다(MockRestServiceServer).
 */
class ExpoPushClientTest {

    private static final String URL = "https://exp.host/--/api/v2/push/send";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private ExpoPushClient client(String accessToken) {
        return new ExpoPushClient(new PushProperties(true, URL, accessToken, 1000, 1000, 3), builder);
    }

    @Test
    void 메시지_배열을_POST하고_티켓을_같은_순서로_돌려준다() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(content().json("""
                        [{"to":"ExponentPushToken[a]","title":"컴퓨터공학과","body":"제목",
                          "data":{"type":"NEWS","newsId":7},"sound":"default","priority":"high"},
                         {"to":"ExponentPushToken[b]"}]
                        """))
                .andRespond(withSuccess("""
                        {"data":[
                          {"status":"ok","id":"XXXX-1"},
                          {"status":"error","message":"\\"ExponentPushToken[b]\\" is not a registered push notification recipient",
                           "details":{"error":"DeviceNotRegistered","expoPushToken":"ExponentPushToken[b]"}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<ExpoPushTicket> tickets = client("secret").send(List.of(
                ExpoPushMessage.of("ExponentPushToken[a]", "컴퓨터공학과", "제목", Map.of("type", "NEWS", "newsId", 7L)),
                ExpoPushMessage.of("ExponentPushToken[b]", "컴퓨터공학과", "제목", Map.of("type", "NEWS", "newsId", 7L))
        ));

        server.verify();
        assertThat(tickets).hasSize(2);
        assertThat(tickets.get(0).status()).isEqualTo("ok");
        assertThat(tickets.get(0).isDeviceNotRegistered()).isFalse();
        assertThat(tickets.get(1).isDeviceNotRegistered()).isTrue();
    }

    @Test
    void 액세스_토큰이_없으면_인증_헤더를_붙이지_않는다() {
        server.expect(requestTo(URL))
                .andExpect(request -> assertThat(request.getHeaders().containsHeader("Authorization")).isFalse())
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        client("").send(List.of());
        server.verify();
    }

    @Test
    void 서버_오류는_예외로_올린다() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client("").send(List.of()))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void 한_번에_100개를_넘기면_보내지_않고_거부한다() {
        ExpoPushMessage message = ExpoPushMessage.of("ExponentPushToken[a]", "t", "b", Map.of());
        assertThatThrownBy(() -> client("").send(Collections.nCopies(101, message)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
