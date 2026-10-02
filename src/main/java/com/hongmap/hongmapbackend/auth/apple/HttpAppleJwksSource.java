package com.hongmap.hongmapbackend.auth.apple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Apple JWKS를 HTTP로 가져온다. 캐시는 {@link AppleIdentityTokenVerifier}가 들고 있다.
 */
@Component
public class HttpAppleJwksSource implements AppleJwksSource {

    private final RestClient restClient;
    private final String keysUrl;

    public HttpAppleJwksSource(AppleProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.keysUrl = properties.getKeysUrl();
    }

    @Override
    public List<AppleJwk> fetchKeys() {
        JwksResponse response = restClient.get().uri(keysUrl).retrieve().body(JwksResponse.class);
        return response != null && response.keys() != null ? response.keys() : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JwksResponse(List<AppleJwk> keys) {
    }
}
