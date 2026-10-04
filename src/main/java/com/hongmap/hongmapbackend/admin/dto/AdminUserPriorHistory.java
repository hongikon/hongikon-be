package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.user.retention.WithdrawRetention;

import java.time.LocalDateTime;

/**
 * 관리자 회원 카드의 "탈퇴 전 이력". 정지·신고 이력으로 탈퇴 기록(withdraw_retentions, 1년 보관)이 남은 계정이
 * 같은 소셜 계정으로 다시 가입했을 때만 채워진다(아니면 AdminUserResponse.priorHistory 가 null).
 *
 * @param withdrawnAt              마지막 탈퇴 시각
 * @param retainUntil              이 기록을 지우는 시각(마지막 탈퇴 + 1년)
 * @param rejoinedAt               재가입 시각
 * @param suspendedAt              탈퇴 전 마지막 정지 시각(정지된 적 없으면 null)
 * @param suspendedReason          그 정지 사유
 * @param wasSuspendedAtWithdrawal 탈퇴 시점에 정지 상태였는지(탈퇴가 여러 번이면 한 번이라도)
 * @param violationReportCount     탈퇴 전 운영진이 위반으로 확정한(반려·삭제) 제보 수 — 보관 중인 제보 요약 수와 같다
 */
public record AdminUserPriorHistory(
        LocalDateTime withdrawnAt,
        LocalDateTime retainUntil,
        LocalDateTime rejoinedAt,
        LocalDateTime suspendedAt,
        String suspendedReason,
        boolean wasSuspendedAtWithdrawal,
        int violationReportCount
) {
    public static AdminUserPriorHistory of(WithdrawRetention record) {
        if (record == null) {
            return null;
        }
        return new AdminUserPriorHistory(
                record.getWithdrawnAt(),
                record.getRetainUntil(),
                record.getRejoinedAt(),
                record.getSuspendedAt(),
                record.getSuspendedReason(),
                record.isWasSuspended(),
                record.getViolationReportCount());
    }
}
