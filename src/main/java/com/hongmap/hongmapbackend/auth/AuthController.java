package com.hongmap.hongmapbackend.auth;

import com.hongmap.hongmapbackend.auth.dto.RefreshTokenRequest;
import com.hongmap.hongmapbackend.auth.dto.TokenExchangeRequest;
import com.hongmap.hongmapbackend.auth.dto.TokenResponse;
import com.hongmap.hongmapbackend.auth.exchange.AuthorizationCodeStore;
import com.hongmap.hongmapbackend.auth.token.RefreshTokenService;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthorizationCodeStore authorizationCodeStore;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "토큰 교환", description = "OAuth 로그인 성공 후 발급된 일회성 code를 access/refresh 토큰으로 교환합니다.")
    @PostMapping("/token/exchange")
    public TokenResponse exchange(@Valid @RequestBody TokenExchangeRequest request) {
        Long userId = authorizationCodeStore.consume(request.code())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 code입니다."));

        return refreshTokenService.issueTokenPair(userId);
    }

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "토큰 재발급", description = "refresh 토큰을 검증한 뒤 새 access/refresh 토큰을 발급합니다(refresh 토큰은 재발급마다 로테이션됩니다).")
    @PostMapping("/reissue")
    public TokenResponse reissue(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokenService.reissue(request.refreshToken());
    }

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "로그아웃", description = "전달받은 refresh 토큰을 서버에서 무효화합니다. 이미 만료/무효한 토큰이어도 성공으로 응답합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "회원탈퇴", description = "Authorization 헤더의 access 토큰으로 식별된 본인 계정과 연관 데이터를 모두 삭제합니다.")
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
