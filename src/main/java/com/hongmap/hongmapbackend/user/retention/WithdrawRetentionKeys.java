package com.hongmap.hongmapbackend.user.retention;

import com.hongmap.hongmapbackend.user.SocialType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * 탈퇴 기록(withdraw_retentions)에 넣는 소셜 계정 식별값. 소셜 id 원문 대신 서버 비밀키로 만든
 * HMAC-SHA256("&lt;social_type&gt;:&lt;social_id&gt;") 의 hex(64자)를 저장한다 — 같은 계정이 다시 가입하면 같은 값이 나와
 * 대조할 수 있고, 키 없이는 원래 id 를 알아낼 수 없다(단순 SHA-256 은 카카오 숫자 id 처럼 범위가 좁으면 전수 대입으로 풀린다).
 *
 * 비밀키: WITHDRAW_RETENTION_KEY_SECRET(app.withdraw-retention.key-secret). 비어 있으면 JWT 비밀키에서 용도 라벨로 파생한다
 * (AuthorKeys 와 같은 방식, 라벨이 달라 값이 겹치지 않는다).
 * 키를 바꾸면 이미 쌓인 기록과 재가입자를 대조할 수 없게 된다 — 바꾸지 않는다(JWT_SECRET 에서 파생 중이면 JWT_SECRET 교체도 같다.
 * 그래서 운영은 별도 키를 두는 것을 권장).
 */
@Slf4j
@Component
public class WithdrawRetentionKeys {

    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public WithdrawRetentionKeys(@Value("${app.withdraw-retention.key-secret:}") String keySecret,
                                 @Value("${jwt.secret:}") String jwtSecret) {
        this.secret = resolveSecret(keySecret, jwtSecret);
        if (secret == null) {
            log.warn("WITHDRAW_RETENTION_KEY_SECRET 과 JWT_SECRET 이 모두 비어 있어 탈퇴 회원 부정 이용 방지 기록을 남기지 않는다.");
        }
    }

    public boolean isEnabled() {
        return secret != null;
    }

    /** 키가 없거나 값이 비면 null. 로그에 원문이 남지 않도록 호출하는 쪽도 socialId 를 찍지 않는다. */
    public String hash(SocialType socialType, String socialId) {
        if (secret == null || socialType == null || socialId == null || socialId.isBlank()) {
            return null;
        }
        return HexFormat.of().formatHex(hmac(secret, socialType.name() + ":" + socialId));
    }

    static byte[] resolveSecret(String keySecret, String jwtSecret) {
        if (keySecret != null && !keySecret.isBlank()) {
            return keySecret.getBytes(StandardCharsets.UTF_8);
        }
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            // JWT 서명 키를 그대로 쓰지 않고 용도 라벨로 한 번 더 HMAC 해 분리한다.
            return hmac(jwtSecret.getBytes(StandardCharsets.UTF_8), "hongikon-withdraw-retention-v1");
        }
        return null;
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 을 쓸 수 없다", e);
        }
    }
}
