package com.hongmap.hongmapbackend.auth.apple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * JWKS의 키 하나(https://appleid.apple.com/auth/keys 의 keys[] 원소). Apple은 RSA(RS256) 키만 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppleJwk(String kty, String kid, String use, String alg, String n, String e) {

    /** n/e(base64url)로 RSA 공개키를 만든다. RSA가 아니거나 값이 깨져 있으면 IllegalArgumentException. */
    public PublicKey toPublicKey() {
        if (!"RSA".equals(kty) || n == null || e == null) {
            throw new IllegalArgumentException("RSA 키가 아닙니다: kid=" + kid);
        }
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    new BigInteger(1, decoder.decode(n)),
                    new BigInteger(1, decoder.decode(e)));
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Apple 공개키를 해석하지 못했습니다: kid=" + kid, ex);
        }
    }
}
