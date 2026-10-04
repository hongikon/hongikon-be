package com.hongmap.hongmapbackend.admin.dto;

import java.time.LocalDateTime;

/**
 * GET /admin/users/{id}/login-name 응답. 이 값을 받을 때마다 admin_pii_access_logs 에 열람 기록이 한 줄 남는다.
 *
 * @param userId        회원 id
 * @param loginNickname 로그인(카카오/Apple) 닉네임 원문. 실명일 수 있으니 화면에 띄워 두지 말고 필요한 동안만 보여 준다
 * @param socialType    KAKAO / APPLE / GOOGLE
 * @param accessedAt    이번 열람 기록 시각
 */
public record AdminLoginNameResponse(Long userId, String loginNickname, String socialType, LocalDateTime accessedAt) {
}
