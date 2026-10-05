package com.hongmap.hongmapbackend.report;

import java.time.LocalDateTime;

/**
 * 관리자가 제보 상태를 바꿨을 때(AdminReportService.moderate) 발행. 커밋 뒤 ReportPushDispatcher가 비동기로 받아 푸시한다.
 * 트랜잭션 밖(비동기 스레드)에서 지연 로딩을 하지 않도록 푸시에 필요한 값을 미리 담는다.
 */
public record ReportModeratedEvent(
        Long reportId,
        Long authorId,
        String title,
        String buildingName,
        Integer floor,
        ReportStatus previousStatus,
        ReportStatus status,
        String note,
        LocalDateTime endsAt,
        /** 시작 시각(UTC). 승인 시점에 아직 시작 전이면 새 제보 알림을 시작 시각으로 미룬다. null 이면 이미 시작한 것으로 본다. */
        LocalDateTime startsAt
) {
    /** 시작 시각을 모르는(이미 시작한 것으로 보는) 이벤트 — 예정 제보 이전의 호출부·테스트 호환용. */
    public ReportModeratedEvent(Long reportId, Long authorId, String title, String buildingName, Integer floor,
                                ReportStatus previousStatus, ReportStatus status, String note, LocalDateTime endsAt) {
        this(reportId, authorId, title, buildingName, floor, previousStatus, status, note, endsAt, null);
    }

    /** 승인 시점(now)에 아직 시작 전인지. */
    public boolean startsAfter(LocalDateTime now) {
        return startsAt != null && startsAt.isAfter(now);
    }
}
