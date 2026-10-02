package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.user.User;

import java.time.LocalDateTime;

/** 관리자 회원 조회·정지 화면의 한 줄. 연락처(email)는 싣지 않는다. */
public record AdminUserResponse(
        Long id,
        String nickname,
        String socialType,
        String role,
        String status,
        String suspendedReason,
        LocalDateTime suspendedAt,
        LocalDateTime createdAt
) {
    public static AdminUserResponse of(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getNickname(),
                user.getSocialType().name(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getSuspendedReason(),
                user.getSuspendedAt(),
                user.getCreatedAt());
    }
}
