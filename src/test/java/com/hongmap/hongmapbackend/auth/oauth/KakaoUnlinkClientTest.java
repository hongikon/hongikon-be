package com.hongmap.hongmapbackend.auth.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 카카오 연결 끊기 요청 형태와 실패 처리. 실제 카카오는 호출하지 않는다(MockRestServiceServer). */
class KakaoUnlinkClientTest {

    private static final String URL = "https://kapi.kakao.com/v1/user/unlink";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void 어드민키로_회원번호를_form_POST한다() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "KakaoAK admin-key"))
                .andExpect(content().contentTypeCompatibleWith("application/x-www-form-urlencoded"))
                .andExpect(content().formDataContains(java.util.Map.of("target_id_type", "user_id", "target_id", "123456")))
                .andRespond(withSuccess("{\"id\":123456}", org.springframework.http.MediaType.APPLICATION_JSON));

        boolean ok = new KakaoUnlinkClient("admin-key", URL, builder).unlink("123456");

        assertThat(ok).isTrue();
        server.verify();
    }

    @Test
    void 카카오_오류는_예외없이_false() {
        server.expect(requestTo(URL)).andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        assertThat(new KakaoUnlinkClient("admin-key", URL, builder).unlink("123456")).isFalse();
        server.verify();
    }

    @Test
    void 어드민키가_없으면_호출하지_않는다() {
        server.expect(never(), requestTo(URL));

        KakaoUnlinkClient client = new KakaoUnlinkClient("  ", URL, builder);
        assertThat(client.isEnabled()).isFalse();
        assertThat(client.unlink("123456")).isFalse();
        client.unlinkAfterCommit("123456");
        server.verify();
    }
}
