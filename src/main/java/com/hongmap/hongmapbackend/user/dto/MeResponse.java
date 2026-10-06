package com.hongmap.hongmapbackend.user.dto;

import com.hongmap.hongmapbackend.user.DisplayNames;
import com.hongmap.hongmapbackend.user.User;

import java.time.LocalDateTime;

/**
 * 내 정보. 로그인 닉네임 원문은 싣지 않는다(앱 화면에 필요 없음).
 *
 * @param appNickname       직접 정한 앱 닉네임. 없으면 null
 * @param displayName       제보 등에서 다른 사람에게 보이는 이름
 * @param maskedDefaultName 앱 닉네임을 지웠을 때 보일 이름(로그인 닉네임을 가린 값)
 * @param status            ACTIVE / SUSPENDED. SUSPENDED 면 앱이 이용 제한 배너(사유·이의 제기 안내)를 띄운다
 * @param suspendedReason   정지 사유(정지 중이 아니면 null)
 * @param suspendedAt       정지 시각(UTC, 정지 중이 아니면 null)
 */
public record MeResponse(
        Long id,
        String socialType,
        String appNickname,
        String displayName,
        String maskedDefaultName,
        String status,
        String suspendedReason,
        LocalDateTime suspendedAt,
        /** 운영진이 붙인 공식 이름(학생회 등). 없으면 null. 있으면 displayName 도 이 값이다 */
        String officialName
) {
    public static MeResponse of(User user) {
        return new MeResponse(
                user.getId(),
                user.getSocialType().name(),
                user.getAppNickname(),
                user.getDisplayName(),
                DisplayNames.mask(user.getNickname()),
                user.getStatus().name(),
                user.getSuspendedReason(),
                user.getSuspendedAt(),
                user.getOfficialName());
    }
}
