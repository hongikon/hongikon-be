package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.user.User;

import java.time.LocalDateTime;

/** 관리자 회원 조회·정지 화면의 한 줄. 연락처(email)는 싣지 않는다. */
public record AdminUserResponse(
        Long id,
        String nickname,
        /** 앱 사용자에게 보이는 이름(앱 닉네임, 없으면 가린 nickname — DisplayNames). 신고·문의 속 이름과 맞춰 볼 때 쓴다. */
        String displayName,
        String socialType,
        String role,
        String status,
        String suspendedReason,
        LocalDateTime suspendedAt,
        LocalDateTime createdAt,
        /** 정지·신고 이력으로 탈퇴 기록이 남은 계정의 재가입이면 그 요약, 아니면 null. 전체는 GET /admin/users/{id}/prior-history */
        AdminUserPriorHistory priorHistory
) {
    public static AdminUserResponse of(User user) {
        return of(user, null);
    }

    public static AdminUserResponse of(User user, AdminUserPriorHistory priorHistory) {
        return new AdminUserResponse(
                user.getId(),
                user.getNickname(),
                user.getDisplayName(),
                user.getSocialType().name(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getSuspendedReason(),
                user.getSuspendedAt(),
                user.getCreatedAt(),
                priorHistory);
    }
}
