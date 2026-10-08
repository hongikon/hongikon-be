package com.hongmap.hongmapbackend.cafeteria;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 요청 주소가 고정 주소 하나뿐인지(SSRF — 학교 엔드포인트가 임의 내부 경로를 중계한다), 크기 상한. 네트워크 없음. */
class CafeteriaMenuClientTest {

    @Test
    void sourceUriIsTheFixedFoodListEndpoint() {
        URI uri = CafeteriaMenuClient.SOURCE_URI;

        assertThat(uri.toString()).isEqualTo("https://www.hongik.ac.kr/sso/APICipher2.jsp?data="
                + "%7B%22url%22%3A%22%2Fhomepage%2Fget_food_list.php%22%2C%22url2%22%3A%22CAMPUS%3D%22%2C%22url3%22%3A%220%22%7D");
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("www.hongik.ac.kr");
        assertThat(uri.getPort()).isEqualTo(-1);
        assertThat(uri.getPath()).isEqualTo("/sso/APICipher2.jsp");
        assertThat(uri.getRawFragment()).isNull();
        String query = uri.getRawQuery();
        assertThat(query).startsWith("data=").doesNotContain("&");
        assertThat(URLDecoder.decode(query.substring("data=".length()), StandardCharsets.UTF_8))
                .isEqualTo("{\"url\":\"/homepage/get_food_list.php\",\"url2\":\"CAMPUS=\",\"url3\":\"0\"}");
    }

    @Test
    void fetchOnlyEverRequestsTheFixedUri() throws Exception {
        List<URI> requested = new ArrayList<>();
        CafeteriaMenuClient client = new CafeteriaMenuClient(uri -> {
            requested.add(uri);
            return "{}";
        });

        client.fetch();
        client.fetch();

        assertThat(requested).hasSize(2).containsOnly(CafeteriaMenuClient.SOURCE_URI);
    }

    @Test
    void bodyIsCappedAtTwoMegabytes() throws IOException {
        int max = CafeteriaMenuClient.MAX_BODY_BYTES;
        assertThat(max).isEqualTo(2 * 1024 * 1024);

        assertThat(CafeteriaMenuClient.readCapped(new ByteArrayInputStream(new byte[max]), max)).hasSize(max);
        assertThatThrownBy(() -> CafeteriaMenuClient.readCapped(new ByteArrayInputStream(new byte[max + 1]), max))
                .isInstanceOf(IOException.class).hasMessageContaining("너무 큼");
    }
}
