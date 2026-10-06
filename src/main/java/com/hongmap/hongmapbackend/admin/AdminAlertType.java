package com.hongmap.hongmapbackend.admin;

/**
 * 관리자 알림(푸시) 종류. dataType은 푸시 data.type 값이고, 프론트 src/utils/notificationRouting.ts가 이 값으로
 * 관리 탭의 어느 섹션을 열지 정한다. idKey는 data에 대상 id를 담는 키.
 */
public enum AdminAlertType {
    /** 새 제보가 등록돼 승인 대기(PENDING) */
    REPORT_PENDING("ADMIN_REPORT_PENDING", "reportId"),
    /** 새 문의(feedback) */
    FEEDBACK("ADMIN_FEEDBACK", "feedbackId"),
    /** 신고가 report.flag.threshold 건 쌓여 제보가 자동 숨김(ACTIVE → HIDDEN) */
    REPORT_FLAGGED("ADMIN_REPORT_FLAGGED", "reportId"),
    /** 정지·신고 이력으로 탈퇴 기록(withdraw_retentions)이 남은 계정이 다시 가입함. 자동 정지는 하지 않는다 — 관리자가 회원 카드에서 판단 */
    MEMBER_REJOINED("ADMIN_MEMBER_REJOINED", "userId"),
    /**
     * 댓글 신고 — 마지막 관리자 검토 뒤 첫 신고가 들어왔을 때, 그리고 신고가 쌓여 자동 숨김됐을 때.
     * data 에 reportId(idKey)와 commentId 를 함께 싣는다(앱은 관리 탭의 "신고된 댓글"을 연다).
     */
    COMMENT_FLAGGED("ADMIN_COMMENT_FLAGGED", "reportId");

    private final String dataType;
    private final String idKey;

    AdminAlertType(String dataType, String idKey) {
        this.dataType = dataType;
        this.idKey = idKey;
    }

    public String dataType() {
        return dataType;
    }

    public String idKey() {
        return idKey;
    }
}
