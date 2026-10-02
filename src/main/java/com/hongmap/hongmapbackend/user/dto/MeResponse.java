package com.hongmap.hongmapbackend.user.dto;

import com.hongmap.hongmapbackend.user.DisplayNames;
import com.hongmap.hongmapbackend.user.User;

/**
 * 내 정보. 로그인 닉네임 원문은 싣지 않는다(앱 화면에 필요 없음).
 *
 * @param appNickname       직접 정한 앱 닉네임. 없으면 null
 * @param displayName       제보 등에서 다른 사람에게 보이는 이름
 * @param maskedDefaultName 앱 닉네임을 지웠을 때 보일 이름(로그인 닉네임을 가린 값)
 */
public record MeResponse(
        Long id,
        String socialType,
        String appNickname,
        String displayName,
        String maskedDefaultName
) {
    public static MeResponse of(User user) {
        return new MeResponse(
                user.getId(),
                user.getSocialType().name(),
                user.getAppNickname(),
                user.getDisplayName(),
                DisplayNames.mask(user.getNickname()));
    }
}
