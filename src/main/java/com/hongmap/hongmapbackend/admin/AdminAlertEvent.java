package com.hongmap.hongmapbackend.admin;

/**
 * 관리자가 처리할 일이 생겼을 때 발행(새 제보·새 문의·신고 자동 숨김). 커밋 뒤 AdminAlertDispatcher가 비동기로 받아
 * ADMIN 유저에게 푸시한다. 트랜잭션 밖에서 지연 로딩을 하지 않도록 푸시에 필요한 값만 미리 담는다.
 * 푸시 본문에는 제보 제목·건물·층만 쓴다 — 작성자·문의 내용·연락처 같은 개인정보는 담지 않는다.
 *
 * @param targetId    제보 id(REPORT_*), 문의 id(FEEDBACK) 또는 재가입한 회원 id(MEMBER_REJOINED)
 * @param actorUserId 이 일을 만든 유저(제보 작성자·문의 작성자·마지막 신고자·재가입자). 본인이 관리자여도 자기 행동 알림은 받지 않는다. 게스트 문의는 null
 * @param reportTitle 제보 제목(FEEDBACK은 null)
 */
public record AdminAlertEvent(
        AdminAlertType type,
        Long targetId,
        Long actorUserId,
        String reportTitle,
        String buildingName,
        Integer floor
) {
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
}
