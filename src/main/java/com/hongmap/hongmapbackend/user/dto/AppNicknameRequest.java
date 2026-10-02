package com.hongmap.hongmapbackend.user.dto;

/** null 이나 빈 문자열이면 앱 닉네임을 지운다. 길이·문자 검사는 AppNicknamePolicy 에서 한다. */
public record AppNicknameRequest(String nickname) {
}
