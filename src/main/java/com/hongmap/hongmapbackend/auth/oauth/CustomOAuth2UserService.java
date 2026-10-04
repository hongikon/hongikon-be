package com.hongmap.hongmapbackend.auth.oauth;

import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.hongmap.hongmapbackend.user.retention.WithdrawRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final WithdrawRetentionService withdrawRetentionService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        KakaoUserInfo userInfo = KakaoUserInfo.from(oAuth2User.getAttributes());

        User user = userRepository.findBySocialTypeAndSocialId(SocialType.KAKAO, userInfo.socialId())
                .orElseGet(() -> {
                    User created = userRepository.save(User.builder()
                            .socialId(userInfo.socialId())
                            .socialType(SocialType.KAKAO)
                            .email(userInfo.email())
                            .nickname(userInfo.nickname())
                            .build());
                    // 정지·신고 이력으로 탈퇴 기록이 남은 계정의 재가입이면 관리자 알림 + 기록 연결(가입은 막지 않음)
                    withdrawRetentionService.onSignup(created);
                    return created;
                });

        return new CustomOAuth2User(user.getId(), oAuth2User.getAttributes());
    }
}
