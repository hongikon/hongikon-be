package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.comment.ReportCommentCounts;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.report.dto.ReportListResponse;
import com.hongmap.hongmapbackend.report.dto.ReportSummaryResponse;
import com.hongmap.hongmapbackend.report.image.ReportImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /reports?sort=hot — 지도 "🔥 HOT" 칩 목록. 지금 진행 중인 공개 제보를 지도 목록과 같은 모양으로 만든 뒤
 * 🔥가 있는 것만 최근 🔥 순으로 추린다(ReportCommunityStats). ReportService 의 목록 메서드 시그니처를 건드리지 않으려고
 * 따로 둔다(#17 의 include=upcoming 변경과 충돌 방지). 캠퍼스 규모(진행 중 제보 수십 개)라 전체를 읽고 거른다.
 */
@Service
@RequiredArgsConstructor
public class ReportHotService {

    private final ReportRepository reportRepository;
    private final ReportImageService reportImageService;
    private final ReportCommentCounts reportCommentCounts;
    private final ReportCommunityStats communityStats;

    @Transactional(readOnly = true)
    public ReportListResponse hotReports(Long requesterId) {
        List<ReportSummaryResponse> body = reportRepository
                .findLiveReports(ReportStatus.ACTIVE, LocalDateTime.now(), null).stream()
                .map(r -> ReportSummaryResponse.of(r, requesterId, reportImageService.viewUrls(r.getImageKeys())))
                .toList();
        return new ReportListResponse(communityStats.attach(reportCommentCounts.attach(body), requesterId,
                ReportCommunityStats.SORT_HOT));
    }
}
