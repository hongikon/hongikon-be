package com.hongmap.hongmapbackend.user.retention;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * withdraw_retentions.snapshot 에 JSON 으로 넣는 내용. 재가입·재탈퇴하면 {@link #withdrawals} 에 한 건씩 이어 붙는다.
 * 부정 이용 판단에 필요한 것만 담는다 — 닉네임·이메일·Apple 토큰·소셜 id 는 넣지 않는다.
 * 관리자 API(GET /admin/users/{id}/prior-history)가 이 구조를 그대로 내려준다.
 */
public record RetentionSnapshot(int version, List<Withdrawal> withdrawals) {

    public static final int VERSION = 1;

    /** 탈퇴 1회분 */
    public record Withdrawal(
            LocalDateTime withdrawnAt,
            String status,
            String suspendedReason,
            LocalDateTime suspendedAt,
            List<ReportEntry> reports,
            List<FiledFlag> flagsFiled
    ) {
    }

    /** 이 회원이 쓴 제보 1건과 그 제보가 받은 신고 */
    public record ReportEntry(
            Long id,
            String category,
            String customCategoryLabel,
            String title,
            String content,
            Long buildingId,
            String buildingName,
            Integer floor,
            BigDecimal lat,
            BigDecimal lng,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String status,
            LocalDateTime createdAt,
            /** S3 에 복사해 둔 사본 키(retained/reports/...). 원본은 탈퇴 커밋 뒤 지워진다. */
            List<String> retainedImageKeys,
            int flagCount,
            List<String> flagReasons
    ) {
    }

    /** 이 회원이 남의 제보에 단 신고 1건 */
    public record FiledFlag(Long reportId, String reason, LocalDateTime createdAt) {
    }
}
