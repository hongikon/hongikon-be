package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.user.User;

import java.time.LocalDateTime;

/**
 * 관리자 회원 조회·정지 화면의 한 줄. memberCode 는 공개 회원 번호(K7Q2M9XA4D).
 * 연락처(email)와 로그인(카카오/Apple) 닉네임 원문은 싣지 않는다 — 로그인 닉네임은 실명인 경우가 많아, 운영진도
 * 평소엔 앱에 보이는 이름과 회원 번호로만 회원을 가리킨다(개인정보 보호법 제3조 최소 처리). 꼭 필요할 때만
 * GET /admin/users/{id}/login-name 으로 열람하고, 그때마다 admin_pii_access_logs 에 기록이 남는다.
 */
public record AdminUserResponse(
        Long id,
        /**
         * 구버전 관리자 화면 호환용 — 예전엔 로그인 닉네임 원문이었지만 이제 displayName 과 같은 값이다.
         * 새 화면은 displayName·appNickname 을 쓴다. 구버전 화면이 모두 사라지면 지운다.
         */
        String nickname,
        /** 앱 사용자에게 보이는 이름(앱 닉네임, 없으면 가린 로그인 닉네임 "홍**" — DisplayNames). 신고·문의 속 이름과 맞춰 볼 때 쓴다. */
        String displayName,
        String socialType,
        String role,
        String status,
        String suspendedReason,
        LocalDateTime suspendedAt,
        LocalDateTime createdAt,
        String memberCode,
        /** 회원이 직접 정한 앱 닉네임. 없으면 null — 화면은 이때 displayName(가린 이름)에 "앱 닉네임 없음"을 함께 보여 준다. */
        String appNickname
) {
    public static AdminUserResponse of(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getDisplayName(),
                user.getSocialType().name(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getSuspendedReason(),
                user.getSuspendedAt(),
                user.getCreatedAt(),
                user.getMemberCode(),
                user.getAppNickname());
    }
}
