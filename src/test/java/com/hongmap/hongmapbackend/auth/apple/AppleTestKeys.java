package com.hongmap.hongmapbackend.auth.apple;

import io.jsonwebtoken.Jwts;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * 테스트용 "가짜 Apple": 로컬에서 만든 RSA 키로 identity token을 서명하고, 같은 키를 JWKS 형태로 내준다.
 */
final class AppleTestKeys {

    static final String CLIENT_ID = "com.hongmap.alimi";

    final String kid;
    final KeyPair keyPair;

    AppleTestKeys(String kid) {
        this.kid = kid;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    AppleJwk jwk() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return new AppleJwk("RSA", kid, "sig", "RS256",
                encoder.encodeToString(unsigned(publicKey.getModulus().toByteArray())),
                encoder.encodeToString(unsigned(publicKey.getPublicExponent().toByteArray())));
    }

    private static byte[] unsigned(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    Builder token() {
        return new Builder();
    }

    final class Builder {
        String issuer = AppleIdentityTokenVerifier.ISSUER;
        String audience = CLIENT_ID;
        String subject = "001234.abcdef0123456789.0123";
        Instant now = Instant.now();
        Instant expiresAt;
        String nonce;
        String headerKid = kid;
        KeyPair signer = keyPair;

        Builder issuer(String value) { issuer = value; return this; }
        Builder audience(String value) { audience = value; return this; }
        Builder subject(String value) { subject = value; return this; }
        Builder now(Instant value) { now = value; return this; }
        Builder expiresAt(Instant value) { expiresAt = value; return this; }
        Builder nonce(String value) { nonce = value; return this; }
        Builder headerKid(String value) { headerKid = value; return this; }
        Builder signedBy(KeyPair value) { signer = value; return this; }

        String build() {
            var builder = Jwts.builder()
                    .header().keyId(headerKid).and()
                    .issuer(issuer)
                    .audience().add(audience).and()
                    .subject(subject)
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiresAt != null ? expiresAt : now.plusSeconds(600)))
                    .claims(Map.of("auth_time", now.getEpochSecond(), "nonce_supported", true));
            if (nonce != null) {
                builder.claim("nonce", nonce);
            }
            return builder.signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
        }
    }
}
