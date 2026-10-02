package com.hongmap.hongmapbackend.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 앱의 Sign in with Apple 결과. identityToken 과 nonce 가 필수.
 *
 * @param identityToken     Apple이 발급한 JWT(서버가 서명·iss·aud·exp·nonce를 검증)
 * @param authorizationCode 탈퇴 시 Apple 토큰 폐기용 refresh 토큰을 받기 위한 1회용 코드(선택)
 * @param fullName          처음 동의할 때만 오는 이름(선택). 첫 가입 닉네임에만 쓴다.
 * @param nonce             앱이 만든 원본 nonce(필수). Apple 에는 sha256hex(원본)을 넘기고, 서버는 토큰의 nonce 와 대조한다(재사용 방지).
 */
public record AppleLoginRequest(
        @NotBlank(message = "identityToken은 필수입니다.") @Size(max = 8192) String identityToken,
        @Size(max = 1024) String authorizationCode,
        @Valid FullName fullName,
        @NotBlank(message = "nonce는 필수입니다.") @Size(max = 512) String nonce
) {

    public record FullName(@Size(max = 100) String givenName, @Size(max = 100) String familyName) {
    }
}
