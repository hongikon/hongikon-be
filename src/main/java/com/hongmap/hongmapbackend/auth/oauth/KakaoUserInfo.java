package com.hongmap.hongmapbackend.auth.oauth;

import java.util.Map;

public record KakaoUserInfo(String socialId, String email, String nickname) {

    @SuppressWarnings("unchecked")
    public static KakaoUserInfo from(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());

        String email = (String) kakaoAccount.get("email");
        String nickname = (String) profile.get("nickname");

        return new KakaoUserInfo(String.valueOf(id), email, nickname != null ? nickname : "카카오사용자");
    }
}
