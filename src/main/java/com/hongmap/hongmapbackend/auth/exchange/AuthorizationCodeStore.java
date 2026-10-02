package com.hongmap.hongmapbackend.auth.exchange;

import java.util.Optional;

public interface AuthorizationCodeStore {

    /** PKCE 없이 발급(구버전 앱 호환). */
    default String issue(Long userId) {
        return issue(userId, null);
    }

    /**
     * @param codeChallenge 로그인 진입 때 앱이 보낸 code_challenge(S256). 있으면 교환 때 같은 code_verifier 가 있어야 한다.
     */
    String issue(Long userId, String codeChallenge);

    /**
     * code 를 1회 소비한다. 발급 때 code_challenge 가 있었으면 codeVerifier 가 맞아야 하고,
     * 틀려도 code 는 소비된다(다시 시도 불가).
     */
    Optional<Long> consume(String code, String codeVerifier);
}
