package com.hongmap.hongmapbackend.auth.exchange;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * RFC 7636(PKCE) S256 검사. 카카오 로그인 뒤 앱 딥링크(hongikon://auth/callback?code=...)로 돌아오는
 * 1회용 code 는 안드로이드에서 같은 스킴을 등록한 다른 앱이 가로챌 수 있다. 로그인을 시작한 앱만 아는
 * code_verifier 를 교환 때 요구해, 가로챈 code 만으로는 토큰을 받을 수 없게 한다.
 */
public final class Pkce {

    /** base64url(SHA-256) 은 패딩 없이 43자. */
    private static final Pattern CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    /** RFC 7636 4.1: unreserved 문자 43~128자. */
    private static final Pattern VERIFIER = Pattern.compile("^[A-Za-z0-9._~-]{43,128}$");

    private Pkce() {
    }

    public static boolean isValidChallenge(String challenge) {
        return challenge != null && CHALLENGE.matcher(challenge).matches();
    }

    public static boolean matches(String codeChallenge, String codeVerifier) {
        if (codeChallenge == null) {
            return true;
        }
        if (codeVerifier == null || !VERIFIER.matcher(codeVerifier).matches()) {
            return false;
        }
        return MessageDigest.isEqual(
                s256(codeVerifier).getBytes(StandardCharsets.US_ASCII),
                codeChallenge.getBytes(StandardCharsets.US_ASCII));
    }

    static String s256(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
