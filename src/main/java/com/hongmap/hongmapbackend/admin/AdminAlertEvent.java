package com.hongmap.hongmapbackend.admin;

/**
 * 관리자가 처리할 일이 생겼을 때 발행(새 제보·새 문의·신고 자동 숨김·댓글 신고). 커밋 뒤 AdminAlertDispatcher가 비동기로 받아
 * ADMIN 유저에게 푸시한다. 트랜잭션 밖에서 지연 로딩을 하지 않도록 푸시에 필요한 값만 미리 담는다.
 * 푸시 본문에는 제보 제목·건물·층만 쓴다 — 작성자·문의 내용·연락처 같은 개인정보는 담지 않는다.
 *
 * @param targetId    제보 id(REPORT_*), 문의 id(FEEDBACK) 또는 재가입한 회원 id(MEMBER_REJOINED)
 * @param actorUserId 이 일을 만든 유저(제보 작성자·문의 작성자·마지막 신고자·재가입자). 본인이 관리자여도 자기 행동 알림은 받지 않는다. 게스트 문의는 null
 * @param reportTitle 제보 제목(FEEDBACK은 null)
 * @param commentId   COMMENT_FLAGGED 일 때 신고된 댓글 id(그 밖에는 null)
 * @param autoHidden  COMMENT_FLAGGED 일 때 이번 신고로 자동 숨김됐는지(제목만 달라진다)
 */
public record AdminAlertEvent(
        AdminAlertType type,
        Long targetId,
        Long actorUserId,
        String reportTitle,
        String buildingName,
        Integer floor,
        Long commentId,
        boolean autoHidden
) {
    public AdminAlertEvent(AdminAlertType type, Long targetId, Long actorUserId, String reportTitle,
                           String buildingName, Integer floor) {
        this(type, targetId, actorUserId, reportTitle, buildingName, floor, null, false);
    }

    public static AdminAlertEvent reportPending(Long reportId, Long authorId, String title, String buildingName, Integer floor) {
        return new AdminAlertEvent(AdminAlertType.REPORT_PENDING, reportId, authorId, title, buildingName, floor);
    }

    public static AdminAlertEvent reportFlagged(Long reportId, Long flaggerId, String title, String buildingName, Integer floor) {
        return new AdminAlertEvent(AdminAlertType.REPORT_FLAGGED, reportId, flaggerId, title, buildingName, floor);
    }

    public static AdminAlertEvent feedback(Long feedbackId, Long userId) {
        return new AdminAlertEvent(AdminAlertType.FEEDBACK, feedbackId, userId, null, null, null);
    }

    /** 정지·신고 이력이 남은 계정의 재가입. 푸시에는 회원 id 만 싣는다(닉네임·소셜 정보 없음). */
    public static AdminAlertEvent memberRejoined(Long userId) {
        return new AdminAlertEvent(AdminAlertType.MEMBER_REJOINED, userId, userId, null, null, null);
    }

    /**
     * 댓글 신고(검토 뒤 첫 신고·자동 숨김). 댓글 내용과 신고자는 싣지 않는다 — 푸시는 잠금 화면에 그대로 보이고,
     * 신고자를 드러내면 보복이 생길 수 있다. 관리자는 관리 탭의 "신고된 댓글"에서 내용을 본다.
     */
    public static AdminAlertEvent commentFlagged(Long reportId, Long commentId, Long flaggerId, String reportTitle,
                                                 boolean autoHidden) {
        return new AdminAlertEvent(AdminAlertType.COMMENT_FLAGGED, reportId, flaggerId, reportTitle, null, null,
                commentId, autoHidden);
    }
}
