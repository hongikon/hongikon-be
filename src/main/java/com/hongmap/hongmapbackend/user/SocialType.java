package com.hongmap.hongmapbackend.user;

/**
 * 로그인 수단. DB 컬럼(users.social_type, withdraw_retentions.social_type)은 VARCHAR(20) 이라 값을 더해도 스키마 변경이 없다.
 * DEMO 는 앱 심사(App Store/TestFlight Beta App Review)용 데모 계정 하나뿐이다(DemoLoginService, POST /auth/demo).
 * 외부 계정이 없어 탈퇴 때 카카오 연결 끊기·Apple 토큰 폐기를 하지 않는다.
 */
public enum SocialType {
    KAKAO, GOOGLE, APPLE, DEMO
}
