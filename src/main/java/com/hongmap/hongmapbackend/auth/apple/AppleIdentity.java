package com.hongmap.hongmapbackend.auth.apple;

/**
 * 검증을 마친 identity token에서 꺼낸 값.
 *
 * @param subject  Apple 사용자 고유 ID(sub). 같은 팀의 앱 사이에서는 변하지 않는다.
 * @param clientId 토큰의 aud(이 토큰을 받은 앱 번들 ID). Apple 토큰 교환·폐기 때 같은 client_id를 써야 한다.
 */
public record AppleIdentity(String subject, String clientId) {
}
