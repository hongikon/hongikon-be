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
        LocalDateTime endsAt
) {
}
