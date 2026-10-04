package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.user.retention.RetentionSnapshot;

import java.util.List;

/**
 * GET /admin/users/{id}/prior-history — 재가입 회원의 탈퇴 전 기록 전체(스냅숏). 관리자 전용.
 *
 * @param userId            재가입한 지금 회원 id
 * @param priorHistory      회원 카드와 같은 요약
 * @param withdrawals       탈퇴 1회마다 제보·받은 신고·단 신고 스냅숏(오래된 순). 닉네임·이메일·소셜 id 는 없다.
 * @param retainedImageUrls 보관 중인 제보 사진 사본의 보기 URL(presigned GET, 1시간). 저장소가 꺼져 있으면 빈 목록.
 */
public record AdminUserPriorHistoryResponse(
        Long userId,
        AdminUserPriorHistory priorHistory,
        List<RetentionSnapshot.Withdrawal> withdrawals,
        List<String> retainedImageUrls
) {
}
