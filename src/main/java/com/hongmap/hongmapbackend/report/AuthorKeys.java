package com.hongmap.hongmapbackend.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * 공개 제보 응답에 싣는 작성자 식별값(authorKey). 앱이 "이 사용자의 제보 숨기기"(기기 저장)에 쓰는 불투명한 값이다.
 * users.id 를 그대로 내보내지 않도록 서버 비밀키로 HMAC-SHA256 을 만들고 앞 16자(base64url, 96비트)만 쓴다.
 * 같은 사용자는 항상 같은 값이고, 키를 모르면 id 를 거꾸로 알아낼 수 없다.
 *
 * 비밀키: AUTHOR_KEY_SECRET(app.report.author-key-secret). 비어 있으면 JWT 비밀키에서 파생한다.
 * 키를 바꾸면 사용자들이 기기에 저장한 숨김 목록이 모두 풀린다(새 값과 맞지 않음) — 바꾸지 않는다.
 *
 * 응답 DTO 의 정적 팩토리(of)에서 쓰기 위해 기동 시 정적 필드에 키를 넣어 둔다.
 */
@Slf4j
@Component
public class AuthorKeys {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_LENGTH = 16;
    private static volatile byte[] secret;

    public AuthorKeys(@Value("${app.report.author-key-secret:}") String authorKeySecret,
                      @Value("${jwt.secret:}") String jwtSecret) {
        secret = resolveSecret(authorKeySecret, jwtSecret);
        if (secret == null) {
            log.warn("AUTHOR_KEY_SECRET 과 JWT_SECRET 이 모두 비어 있어 제보 응답에 authorKey 를 싣지 않는다.");
        }
    }

    /** 사용자 id 의 authorKey. 키가 없거나 id 가 null 이면 null(앱은 숨기기 메뉴를 감춘다). */
    public static String of(Long userId) {
        byte[] key = secret;
        if (key == null || userId == null) {
            return null;
        }
        byte[] digest = hmac(key, "report-author:" + userId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, KEY_LENGTH);
    }

    static byte[] resolveSecret(String authorKeySecret, String jwtSecret) {
        if (authorKeySecret != null && !authorKeySecret.isBlank()) {
            return authorKeySecret.getBytes(StandardCharsets.UTF_8);
        }
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            // JWT 서명 키를 그대로 쓰지 않고 용도 라벨로 한 번 더 HMAC 해 분리한다.
            return hmac(jwtSecret.getBytes(StandardCharsets.UTF_8), "hongikon-author-key-v1");
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
