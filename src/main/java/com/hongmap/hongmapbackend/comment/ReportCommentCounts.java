package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.report.dto.ReportSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 지도 제보 목록에 공개 댓글 수를 붙인다 — 목록 전체를 쿼리 한 번(IN + GROUP BY)으로 센다. */
@Component
@RequiredArgsConstructor
public class ReportCommentCounts {

    private final ReportCommentRepository commentRepository;

    public List<ReportSummaryResponse> attach(List<ReportSummaryResponse> reports) {
        if (reports.isEmpty()) {
            return reports;
        }
        List<Long> ids = reports.stream().map(ReportSummaryResponse::id).toList();
        Map<Long, Long> counts = commentRepository.countVisibleByReportIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return reports.stream().map(r -> r.withCommentCount(counts.getOrDefault(r.id(), 0L))).toList();
    }
}
