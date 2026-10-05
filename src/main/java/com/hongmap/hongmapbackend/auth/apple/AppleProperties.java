package com.hongmap.hongmapbackend.auth.apple;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * application.properties의 app.apple.* 값 바인딩 (Sign in with Apple).
 *
 * <p>identity token 검증은 clientIds만 있으면 된다. teamId/keyId/privateKey는 탈퇴 시 Apple 토큰 폐기(revoke)용이라
 * 비어 있어도 로그인은 정상 동작한다 — 그 경우 Apple refresh 토큰을 받아 두지 않고 탈퇴 때 폐기도 건너뛴다(WARN 로그).
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.apple")
public class AppleProperties {

    /** identity token의 aud로 허용할 값(=iOS 번들 ID). 프로덕션·프리뷰·개발 빌드의 번들 ID를 모두 넣는다. */
    private final List<String> clientIds;

    /** Apple Developer 팀 ID(10자리). client_secret JWT의 iss. */
    private final String teamId;

    /** Sign in with Apple 용 키의 Key ID. client_secret JWT 헤더의 kid. */
    private final String keyId;

    /** 위 키의 .p8 파일 내용(PEM, PKCS#8). 환경 변수로 넣을 때 줄바꿈 대신 \n 문자열을 써도 된다. */
    private final String privateKey;

    /** Apple 공개키(JWKS) 주소. */
    private final String keysUrl;

    /** authorization code → refresh 토큰 교환 주소. */
    private final String tokenUrl;

    /** 토큰 폐기 주소. */
    private final String revokeUrl;

    private final int connectTimeoutMs;

    private final int readTimeoutMs;

    /**
     * 웹(hongikon.com) Apple 로그인의 Services ID. 웹에서 받은 identity token 의 aud 가 이 값이다.
     * 비우면 웹 Apple 로그인을 받지 않는다(앱 번들 ID 만 허용).
     */
    private final String webClientId;

    /** 웹 Apple 로그인 돌아올 주소. Apple 콘솔 Services ID 의 Return URL 과 같아야 하고, 웹 로그인의 code 교환에 함께 보낸다. */
    private final String webRedirectUri;

    /** identity token 의 aud 로 허용하는 값 전부(앱 번들 ID + 웹 Services ID). */
    public List<String> allClientIds() {
        List<String> ids = new java.util.ArrayList<>(clientIds == null ? List.of() : clientIds);
        if (hasText(webClientId)) ids.add(webClientId.trim());
        return ids;
    }

    /** 이 client_id 가 웹 Services ID 인지(웹은 code 교환에 redirect_uri 가 필요하다). */
    public boolean isWebClient(String clientId) {
        return hasText(webClientId) && webClientId.trim().equals(clientId);
    }

    /** Apple 토큰 교환·폐기에 필요한 키 설정이 모두 들어 있는지. */
    public boolean isRevocationConfigured() {
        return hasText(teamId) && hasText(keyId) && hasText(privateKey);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
