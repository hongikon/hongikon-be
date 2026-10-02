package com.hongmap.hongmapbackend.auth.apple;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * prod(app.apple.require-revocation=true)에서 Apple 로그인이 켜져 있는데(APPLE_CLIENT_IDS 가 비어 있지 않음)
 * 토큰 폐기·암호화 설정이 빠졌으면 기동을 멈춘다. 키 없이 운영하면 탈퇴 시 Apple 연결이 남아 App Store 5.1.1(v) 위반이다.
 * Apple 로그인을 일부러 끄려면 APPLE_CLIENT_IDS 를 빈 값으로 둔다(그러면 /auth/apple 은 모두 401).
 */
@Component
public class AppleStartupCheck implements InitializingBean {

    private final AppleProperties properties;
    private final AppleAuthClient appleAuthClient;
    private final AppleTokenCipher tokenCipher;
    private final boolean required;

    public AppleStartupCheck(AppleProperties properties, AppleAuthClient appleAuthClient, AppleTokenCipher tokenCipher,
                             @Value("${app.apple.require-revocation:false}") boolean required) {
        this.properties = properties;
        this.appleAuthClient = appleAuthClient;
        this.tokenCipher = tokenCipher;
        this.required = required;
    }

    @Override
    public void afterPropertiesSet() {
        boolean loginEnabled = properties.getClientIds() != null
                && properties.getClientIds().stream().anyMatch(id -> id != null && !id.isBlank());
        if (!required || !loginEnabled) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (!appleAuthClient.isConfigured()) {
            missing.add("APPLE_TEAM_ID/APPLE_KEY_ID/APPLE_PRIVATE_KEY(비었거나 .p8 형식 오류)");
        }
        if (!tokenCipher.isEnabled()) {
            missing.add("APPLE_TOKEN_ENC_KEY");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Apple 로그인이 켜져 있는데 " + String.join(", ", missing)
                    + " 설정이 없습니다. 설정하거나, Apple 로그인을 끄려면 APPLE_CLIENT_IDS 를 빈 값으로 두세요.");
        }
    }
}
