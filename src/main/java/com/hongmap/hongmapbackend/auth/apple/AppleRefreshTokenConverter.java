package com.hongmap.hongmapbackend.auth.apple;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * users.apple_refresh_token, apple_pending_revocations.refresh_token 을 AES-GCM 으로 암호화해 저장한다.
 * Spring Boot 가 Hibernate 에 Spring 빈 컨테이너를 연결하므로 이 컨버터에도 {@link AppleTokenCipher} 가 주입된다.
 *
 * <ul>
 *   <li>쓰기: 키가 없으면 예외(평문으로 저장하지 않는다). 호출 쪽({@link AppleLoginService})이 키가 없으면 애초에 저장하지 않는다.</li>
 *   <li>읽기: {@code v1:} 형식이면 복호화. 복호화할 수 없으면(키 교체·손상) null 로 읽고 WARN — 그 사용자는 폐기를 건너뛰고,
 *       다음 로그인 때 새 토큰으로 바뀐다. {@code v1:} 이 아닌 값은 암호화 도입 전 평문으로 보고 그대로 읽는다(다음 저장 때 암호화).</li>
 * </ul>
 */
@Slf4j
@Component
@Converter
public class AppleRefreshTokenConverter implements AttributeConverter<String, String> {

    private final AppleTokenCipher cipher;

    public AppleRefreshTokenConverter(AppleTokenCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return plaintext == null ? null : cipher.encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(AppleTokenCipher.PREFIX)) {
            return stored;
        }
        try {
            return cipher.decrypt(stored);
        } catch (IllegalStateException e) {
            log.warn("저장된 Apple refresh 토큰을 읽지 못했습니다: {}", e.getMessage());
            return null;
        }
    }
}
