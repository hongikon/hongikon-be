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
    REPORT_FLAGGED("ADMIN_REPORT_FLAGGED", "reportId");

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
