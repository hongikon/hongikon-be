package com.hongmap.hongmapbackend.auth.apple;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Apple refresh 토큰 저장용 AES-256-GCM 암호화. 키는 환경 변수 {@code APPLE_TOKEN_ENC_KEY}(32바이트를 Base64 로, 예:
 * {@code openssl rand -base64 32}). DB 가 유출돼도 토큰만으로는 Apple 연결을 조작할 수 없게 한다(점검 M4).
 *
 * <p>저장 형식: {@code v1:} + Base64(IV 12바이트 ‖ 암호문 ‖ 태그 16바이트). 키가 비어 있으면 {@link #isEnabled()} 가 false 이고
 * 토큰을 저장하지 않는다(평문 저장 금지). 키 형식이 틀리면 기동하지 않는다.
 * 키를 바꾸면 기존 토큰은 읽지 못한다(그 사용자는 다음 로그인 때 새 토큰으로 바뀐다).
 */
@Component
public class AppleTokenCipher {

    static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AppleTokenCipher(@Value("${app.apple.token-encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APPLE_TOKEN_ENC_KEY 는 Base64 여야 합니다(openssl rand -base64 32).");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("APPLE_TOKEN_ENC_KEY 는 32바이트(AES-256)여야 합니다. 현재 " + raw.length + "바이트.");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public boolean isEnabled() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        if (key == null) {
            throw new IllegalStateException("APPLE_TOKEN_ENC_KEY 가 없어 Apple 토큰을 암호화할 수 없습니다.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Apple 토큰 암호화 실패", e);
        }
    }

    /** {@link #encrypt} 결과를 되돌린다. 키가 없거나 다르거나 값이 변조됐으면 IllegalStateException. */
    public String decrypt(String stored) {
        if (key == null) {
            throw new IllegalStateException("APPLE_TOKEN_ENC_KEY 가 없어 Apple 토큰을 복호화할 수 없습니다.");
        }
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("암호화된 Apple 토큰 형식이 아닙니다.");
        }
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (data.length <= IV_BYTES) {
                throw new IllegalStateException("암호화된 Apple 토큰이 너무 짧습니다.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Apple 토큰 복호화 실패(키가 바뀌었거나 값이 손상됨)", e);
        }
    }
}
