package com.hongmap.hongmapbackend.user.retention;

import java.time.LocalDateTime;
import java.util.List;

/**
 * withdraw_retentions.snapshot 에 JSON 으로 넣는 내용. 재가입·재탈퇴하면 {@link #withdrawals} 에 한 건씩 이어 붙는다.
 * 개인정보 보호법 최소 수집(제3조·제16조)·정당한 이익의 필요성·비례성(제15조 제1항 제6호)에 맞춰, 운영진이 위반으로 확정한 제보의
 * 요약만 담는다 — 위치(건물·층·좌표)·기간, 위반이 아닌 제보, 이 회원이 남에게 단 신고는 넣지 않는다.
 * 닉네임·이메일·Apple 토큰·소셜 id 도 없다. 관리자 API(GET /admin/users/{id}/prior-history)가 이 구조를 그대로 내려준다.
 */
public record RetentionSnapshot(int version, List<Withdrawal> withdrawals) {

    /** 2: 위반 확정 제보 요약만(2026-10-04 범위 축소). 1 은 배포 전이라 운영 데이터에 없다. */
    public static final int VERSION = 2;

    /** 탈퇴 1회분 */
    public record Withdrawal(
            LocalDateTime withdrawnAt,
            String status,
            String suspendedReason,
            LocalDateTime suspendedAt,
            List<ViolationReport> violationReports
    ) {
    }

    /**
     * 운영진이 위반으로 확정한(반려 REJECTED·삭제 DELETED 처리한) 제보 1건의 요약.
     *
     * @param content           본문 앞 {@link WithdrawRetentionService#CONTENT_SUMMARY_LENGTH}자(넘으면 "…")
     * @param moderationNote    관리자가 남긴 반려·삭제 사유(없으면 null)
     * @param retainedImageKeys 증거로 복사해 둔 사진 사본 키(retained/reports/...). 없으면 빈 목록
     */
    public record ViolationReport(
            Long id,
            String category,
            String customCategoryLabel,
            String title,
            String content,
            String status,
            LocalDateTime createdAt,
            String moderationNote,
            int flagCount,
            List<String> flagReasons,
            List<String> retainedImageKeys
    ) {
    }
}
