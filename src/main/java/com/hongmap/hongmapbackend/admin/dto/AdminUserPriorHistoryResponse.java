package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.user.retention.RetentionSnapshot;

import java.util.List;

/**
 * GET /admin/users/{id}/prior-history — 재가입 회원의 탈퇴 전 기록 전체(스냅숏). 관리자 전용.
 *
 * @param userId            재가입한 지금 회원 id
 * @param priorHistory      회원 카드와 같은 요약
 * @param withdrawals       탈퇴 1회마다 정지 정보와 위반 확정(관리자 삭제) 제보 요약(오래된 순). 닉네임·이메일·소셜 id·위치·사진은 없다.
 */
public record AdminUserPriorHistoryResponse(
        Long userId,
        AdminUserPriorHistory priorHistory,
        List<RetentionSnapshot.Withdrawal> withdrawals
) {
}
