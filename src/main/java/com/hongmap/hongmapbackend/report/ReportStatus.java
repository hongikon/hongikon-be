package com.hongmap.hongmapbackend.report;

public enum ReportStatus {
    /** 등록 직후 기본값. 운영진 승인 대기 */
    PENDING,
    /** 운영진 승인 완료. 지도에 노출 */
    ACTIVE,
    /** 운영진 반려 */
    REJECTED,
    /** 신고 누적으로 자동 숨김 */
    HIDDEN,
    DELETED
}
