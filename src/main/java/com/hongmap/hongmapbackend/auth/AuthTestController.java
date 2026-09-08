package com.hongmap.hongmapbackend.auth;

import com.hongmap.hongmapbackend.auth.dto.TokenResponse;
import com.hongmap.hongmapbackend.auth.token.RefreshTokenService;
import com.hongmap.hongmapbackend.common.config.SwaggerConfig;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 프론트 개발/Swagger 테스트 편의를 위해 OAuth 로그인 없이 고정 테스트 계정의 JWT를 즉시 발급하는 컨트롤러.
// 인증 우회 수단이므로 local 프로필에서만 빈으로 등록되도록 가드한다.
@Profile("local")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthTestController {

    // 프론트 테스트 편의용 고정 테스트 계정 식별자. /auth/test-token 전용이며 그 외 용도로 사용하지 않는다.
    private static final String TEST_ACCOUNT_SOCIAL_ID = "test-account";

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Tag(name = SwaggerConfig.TAG_AUTH_MYPAGE)
    @Operation(summary = "테스트 토큰 발급", description = "OAuth 로그인 없이 고정 테스트 계정의 JWT를 즉시 발급합니다. (local 프로필 전용)")
    @PostMapping("/test-token")
    public TokenResponse issueTestToken() {
        User testUser = userRepository.findBySocialTypeAndSocialId(SocialType.KAKAO, TEST_ACCOUNT_SOCIAL_ID)
                .orElseGet(() -> userRepository.save(User.builder()
                        .socialType(SocialType.KAKAO)
                        .socialId(TEST_ACCOUNT_SOCIAL_ID)
                        .email("test-account@hongmap.local")
                        .nickname("테스트계정")
                        .build()));

        return refreshTokenService.issueTokenPair(testUser.getId());
    }
}
