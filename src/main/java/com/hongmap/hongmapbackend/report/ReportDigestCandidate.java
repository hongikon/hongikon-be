package com.hongmap.hongmapbackend.report;

import java.time.LocalDateTime;

/** 새 제보 알림 다이제스트 후보(ReportRepository.findDigestCandidates) — 푸시 문구에 필요한 값만. publishedAt 은 UTC. */
public record ReportDigestCandidate(
        Long reportId,
        Long authorId,
        String title,
        String buildingName,
        Integer floor,
        LocalDateTime publishedAt
) {
}
