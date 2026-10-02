package com.hongmap.hongmapbackend.auth.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * 회원탈퇴 시 카카오 "연결 끊기"(POST /v1/user/unlink, 어드민 키 방식). 카카오 쪽에서도 홍익온과의 연결과
 * 동의 항목이 지워져, 다시 로그인하면 처음 가입처럼 동의 화면이 나온다.
 *
 * 최선 노력(best-effort): 탈퇴(DB 삭제)가 커밋된 뒤에만 호출하고, 실패해도 탈퇴는 그대로 완료된다(경고 로그).
 * KAKAO_ADMIN_KEY 가 비어 있으면 호출하지 않는다.
 */
@Slf4j
@Component
public class KakaoUnlinkClient {

    private final RestClient restClient;
    private final String adminKey;
    private final String unlinkUrl;

    @Autowired
    public KakaoUnlinkClient(@Value("${app.kakao.admin-key:}") String adminKey,
                             @Value("${app.kakao.unlink-url:https://kapi.kakao.com/v1/user/unlink}") String unlinkUrl) {
        this(adminKey, unlinkUrl, RestClient.builder().requestFactory(requestFactory()));
    }

    /** 테스트에서 MockRestServiceServer 를 붙인 builder 를 넘기기 위한 생성자. */
    KakaoUnlinkClient(String adminKey, String unlinkUrl, RestClient.Builder builder) {
        this.adminKey = adminKey == null ? "" : adminKey.trim();
        this.unlinkUrl = unlinkUrl;
        this.restClient = builder.build();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    public boolean isEnabled() {
        return !adminKey.isEmpty();
    }

    /** 트랜잭션 안이면 커밋된 뒤에, 밖이면 바로 연결을 끊는다(롤백되면 호출하지 않는다). */
    public void unlinkAfterCommit(String kakaoUserId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            unlink(kakaoUserId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                unlink(kakaoUserId);
            }
        });
    }

    /** 성공하면 true. 키가 없거나 실패하면 false(예외를 밖으로 던지지 않는다). */
    public boolean unlink(String kakaoUserId) {
        if (!isEnabled()) {
            log.warn("KAKAO_ADMIN_KEY 가 없어 카카오 연결 끊기를 건너뛴다.");
            return false;
        }
        if (kakaoUserId == null || kakaoUserId.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", kakaoUserId);
        try {
            restClient.post()
                    .uri(unlinkUrl)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("카카오 연결 끊기 완료");
            return true;
        } catch (RestClientException e) {
            // 카카오 회원번호는 개인정보라 로그에 남기지 않는다.
            log.warn("카카오 연결 끊기 실패(탈퇴는 완료됨): {}", e.getMessage());
            return false;
        }
    }
}
