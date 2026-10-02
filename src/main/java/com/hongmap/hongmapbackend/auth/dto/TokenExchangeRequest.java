package com.hongmap.hongmapbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param codeVerifier 로그인 진입 때 code_challenge 를 보냈다면 그 원문(PKCE). 구버전 앱은 보내지 않는다.
 */
public record TokenExchangeRequest(
        @NotBlank(message = "code는 필수입니다.") String code,
        @Size(max = 128) String codeVerifier
) {
}
