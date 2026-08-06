package com.hongmap.hongmapbackend.auth.oauth;

import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
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

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        KakaoUserInfo userInfo = KakaoUserInfo.from(oAuth2User.getAttributes());

        User user = userRepository.findBySocialTypeAndSocialId(SocialType.KAKAO, userInfo.socialId())
                .orElseGet(() -> userRepository.save(User.builder()
                        .socialId(userInfo.socialId())
                        .socialType(SocialType.KAKAO)
                        .email(userInfo.email())
                        .nickname(userInfo.nickname())
                        .build()));

        return new CustomOAuth2User(user.getId(), oAuth2User.getAttributes());
    }
}
