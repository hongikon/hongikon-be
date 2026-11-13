package com.hongmap.hongmapbackend.common.devkey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 개발용 페이지(/dev/*) 암호키 확인. 앱 DevPathGate 가 sessionStorage 에 둔 암호키 원문을 헤더(X-Dev-Key)로 보내고, 서버는 그 SHA-256 이
 * ask.dev-key-sha256(ASK_DEV_KEY_SHA256 — /dev/ask 와 같은 키)과 같은지 본다. 키가 설정되지 않은 서버에서는 404(엔드포인트가 없는 것처럼).
 */
@Component
public class DevKeyGuard {

    public static final String HEADER = "X-Dev-Key";

    private final byte[] keySha256;

    public DevKeyGuard(@Value("${ask.dev-key-sha256:}") String keySha256) {
        this.keySha256 = keySha256 == null || keySha256.isBlank() ? null : HexFormat.of().parseHex(keySha256.trim());
    }

    public void check(String key) {
        if (keySha256 == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (key == null || !MessageDigest.isEqual(keySha256, sha256(key))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
