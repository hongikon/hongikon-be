package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.report.dto.ReportSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 지도 제보 목록에 🔥 수·HOT·내 🔥/관심·조회 수·작성자 알림을 붙인다 — 목록 전체를 네이티브 쿼리 한 번으로 읽는다.
 * sort=hot 이면 🔥가 하나라도 있는 제보만 최근 🔥 수 → 전체 🔥 수 → 최신 순으로 최대 {@value #HOT_LIMIT}개.
 */
@Component
@RequiredArgsConstructor
public class ReportCommunityStats {

    public static final String SORT_HOT = "hot";
    static final int HOT_LIMIT = 20;

    private final ReportReactionRepository reactionRepository;
    private final ReportCommunityService communityService;

    public List<ReportSummaryResponse> attach(List<ReportSummaryResponse> reports, Long requesterId, String sort) {
        if (reports.isEmpty()) {
            return reports;
        }
        List<Long> ids = reports.stream().map(ReportSummaryResponse::id).toList();
        Map<Long, Object[]> rows = new HashMap<>();
        for (Object[] row : reactionRepository.findStats(ids, communityService.hotSince(),
                requesterId == null ? -1L : requesterId)) {
            rows.put(toLong(row[0]), row);
        }
        long threshold = communityService.hotThreshold();
        List<ReportSummaryResponse> result = reports.stream().map(r -> {
            Object[] row = rows.get(r.id());
            long fire = row == null ? 0 : toLong(row[1]);
            long recent = row == null ? 0 : toLong(row[2]);
            boolean firedByMe = row != null && toLong(row[3]) > 0;
            boolean followedByMe = row != null && toLong(row[4]) > 0;
            long views = row == null || row[5] == null ? 0 : toLong(row[5]);
            Boolean notify = r.isMine()
                    ? (row == null || row[6] == null ? ReportEngagement.DEFAULT_AUTHOR_NOTIFY_ENABLED : toBoolean(row[6]))
                    : null;
            return r.withCommunity(fire, recent, recent >= threshold, firedByMe, followedByMe, views, notify);
        }).toList();

        if (!SORT_HOT.equalsIgnoreCase(sort)) {
            return result;
        }
        return result.stream()
                .filter(r -> r.fireCount() > 0)
                .sorted(Comparator.comparing(ReportSummaryResponse::recentFireCount, Comparator.reverseOrder())
                        .thenComparing(ReportSummaryResponse::fireCount, Comparator.reverseOrder())
                        .thenComparing(ReportSummaryResponse::id, Comparator.reverseOrder()))
                .limit(HOT_LIMIT)
                .toList();
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return ((Number) value).intValue() != 0;
    }
}
