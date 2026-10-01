package com.hongmap.hongmapbackend.push;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Expo Push API 호출만 담당한다(재시도·대상 선정 없음). 호출 실패는 예외로 그대로 올린다 — 격리는 호출하는 쪽(NewsPushDispatcher) 책임.
 */
@Component
public class ExpoPushClient {

    /** Expo가 요청 하나에 받는 최대 메시지 수. */
    public static final int MAX_BATCH_SIZE = 100;

    private final RestClient restClient;

    @Autowired
    public ExpoPushClient(PushProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties)));
    }

    /** 테스트에서 MockRestServiceServer를 붙인 builder를 넘기기 위한 생성자(requestFactory는 건드리지 않는다). */
    ExpoPushClient(PushProperties properties, RestClient.Builder builder) {
        builder.baseUrl(properties.getExpoUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (properties.getExpoAccessToken() != null && !properties.getExpoAccessToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getExpoAccessToken());
        }
        this.restClient = builder.build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(PushProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return requestFactory;
    }

    /**
     * 메시지 최대 {@value #MAX_BATCH_SIZE}개를 한 요청으로 보낸다. 반환하는 티켓은 messages와 같은 순서다.
     * HTTP 오류·네트워크 오류는 RestClientException으로 던진다.
     */
    public List<ExpoPushTicket> send(List<ExpoPushMessage> messages) {
        if (messages.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Expo는 요청당 최대 " + MAX_BATCH_SIZE + "개까지 받는다: " + messages.size());
        }
        SendResponse response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(messages)
                .retrieve()
                .body(SendResponse.class);
        return response != null && response.data() != null ? response.data() : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SendResponse(List<ExpoPushTicket> data) {
    }
}
