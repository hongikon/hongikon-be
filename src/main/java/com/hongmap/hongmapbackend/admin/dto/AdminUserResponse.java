package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.user.User;

import java.time.LocalDateTime;

/** 관리자 회원 조회·정지 화면의 한 줄. 연락처(email)는 싣지 않는다. memberCode 는 공개 회원 번호(K7Q2M9XA4D). */
public record AdminUserResponse(
        Long id,
        String nickname,
        String socialType,
        String role,
        String status,
        String suspendedReason,
        LocalDateTime suspendedAt,
        LocalDateTime createdAt,
        String memberCode
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
                user.getCreatedAt(),
                user.getMemberCode());
    }
}
