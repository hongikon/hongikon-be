package com.hongmap.hongmapbackend.auth;

import com.hongmap.hongmapbackend.auth.dto.TokenExchangeRequest;
import com.hongmap.hongmapbackend.auth.dto.TokenResponse;
import com.hongmap.hongmapbackend.auth.exchange.AuthorizationCodeStore;
import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    // 프론트 테스트 편의용 고정 테스트 계정 식별자. /auth/test-token 전용이며 그 외 용도로 사용하지 않는다.
    private static final String TEST_ACCOUNT_SOCIAL_ID = "test-account";

    private final AuthorizationCodeStore authorizationCodeStore;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/token/exchange")
    public TokenResponse exchange(@Valid @RequestBody TokenExchangeRequest request) {
        Long userId = authorizationCodeStore.consume(request.code())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 code입니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return new TokenResponse(accessToken, refreshToken);
    }

    // TODO(배포 전 필수 제거): 프론트 개발/Swagger 테스트 편의를 위해 OAuth 로그인 없이
    // 고정 테스트 계정의 JWT를 즉시 발급하는 엔드포인트. 인증 우회 수단이므로 운영 배포 전 반드시 삭제할 것.
    @PostMapping("/test-token")
    public TokenResponse issueTestToken() {
        User testUser = userRepository.findBySocialTypeAndSocialId(SocialType.KAKAO, TEST_ACCOUNT_SOCIAL_ID)
                .orElseGet(() -> userRepository.save(User.builder()
                        .socialType(SocialType.KAKAO)
                        .socialId(TEST_ACCOUNT_SOCIAL_ID)
                        .email("test-account@hongmap.local")
                        .nickname("테스트계정")
                        .build()));

        String accessToken = jwtTokenProvider.generateAccessToken(testUser.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUser.getId());

        return new TokenResponse(accessToken, refreshToken);
    }
}
