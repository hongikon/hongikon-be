package com.hongmap.hongmapbackend.comment;

/** 제보 댓글 상태. 공개 목록에는 VISIBLE 만 나간다. */
public enum ReportCommentStatus {
    /** 기본값. 작성 즉시 공개(사후 검토) */
    VISIBLE,
    /** 신고 누적 자동 숨김 또는 관리자 숨김. 관리자가 다시 공개(VISIBLE)할 수 있다 */
    HIDDEN,
    /** 작성자 삭제 또는 관리자 삭제. 신고 검토·악용 방지 근거로 행은 제보가 지워질 때까지 남는다 */
    DELETED
}
