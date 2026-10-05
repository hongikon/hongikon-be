package com.hongmap.hongmapbackend.comment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * 댓글 응답의 authorKey(작성자 숨기기용 불투명 값). PR #13 의 report.AuthorKeys 와 <b>같은 비밀키·같은 입력·같은 길이</b>로 만든다 —
 * 그래서 앱이 "이 사용자 숨기기"로 저장한 값이 제보와 댓글에 똑같이 맞는다. users.id 는 응답에 싣지 않는다.
 *
 * <p>#13 이 아직 main 에 없어서 이 PR 은 같은 계산을 따로 갖고 있다. #13 머지 뒤 {@code AuthorKeys.of(userId)} 로 바꾸고
 * 이 클래스를 지워도 값은 그대로다(CommentAuthorKeysTest 가 #13 과 같은 결과를 고정해 둔다).
 */
@Component
public class CommentAuthorKeys {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_LENGTH = 16;

    private final byte[] secret;

    public CommentAuthorKeys(@Value("${app.report.author-key-secret:}") String authorKeySecret,
                             @Value("${jwt.secret:}") String jwtSecret) {
        this.secret = resolveSecret(authorKeySecret, jwtSecret);
    }

    /** 키가 없거나 id 가 null 이면 null(앱은 숨기기 메뉴를 감춘다). */
    public String of(Long userId) {
        if (secret == null || userId == null) {
            return null;
        }
        byte[] digest = hmac(secret, "report-author:" + userId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, KEY_LENGTH);
    }

    static byte[] resolveSecret(String authorKeySecret, String jwtSecret) {
        if (authorKeySecret != null && !authorKeySecret.isBlank()) {
            return authorKeySecret.getBytes(StandardCharsets.UTF_8);
        }
        if (jwtSecret != null && !jwtSecret.isBlank()) {
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
