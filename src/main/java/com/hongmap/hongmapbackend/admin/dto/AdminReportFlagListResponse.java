package com.hongmap.hongmapbackend.admin.dto;

import com.hongmap.hongmapbackend.report.ReportFlag;
import com.hongmap.hongmapbackend.user.DisplayNames;
import com.hongmap.hongmapbackend.user.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 제보에 달린 신고 목록(관리자). 신고자는 앱에 보이는 이름과 회원 번호로만 가리키고 로그인 닉네임 원문은 싣지 않는다
 * (개인정보 보호법 제3조 최소 처리 — 원문 열람은 GET /admin/users/{id}/login-name, 열람 기록이 남는다).
 */
public record AdminReportFlagListResponse(List<Item> flags) {

    /**
     * @param reporterNickname    구버전 관리자 화면 호환용. 예전엔 로그인 닉네임 원문이었지만 이제 reporterDisplayName 과 같은 값
     * @param reporterDisplayName 앱 닉네임, 없으면 가린 로그인 닉네임("홍**")
     * @param reporterMemberCode  공개 회원 번호(K7Q2M9XA4D)
     */
    public record Item(Long id, String reason, String reporterNickname, LocalDateTime createdAt,
                       Long reporterId, String reporterDisplayName, String reporterMemberCode) {
        public static Item of(ReportFlag flag) {
            User reporter = flag.getUser();
            // 신고자는 탈퇴 때 신고째 지워지므로(UserService.withdraw) 보통 null 이 아니지만, 혹시 몰라 익명으로 둔다.
            String displayName = reporter != null ? reporter.getDisplayName() : DisplayNames.ANONYMOUS;
            return new Item(flag.getId(), flag.getReason(), displayName, flag.getCreatedAt(),
                    reporter != null ? reporter.getId() : null, displayName,
                    reporter != null ? reporter.getMemberCode() : null);
        }
    }
}
