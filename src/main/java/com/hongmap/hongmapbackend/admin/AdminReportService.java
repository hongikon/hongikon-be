package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.admin.dto.AdminReportFlagListResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminReportListResponse;
import com.hongmap.hongmapbackend.admin.dto.AdminReportResponse;
import com.hongmap.hongmapbackend.admin.dto.ReportModerationRequest;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportFlagRepository;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.report.image.ReportImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 제보 검토. 제보는 PENDING 으로 등록되고 지도에는 ACTIVE 만 뜨므로, 관리자 승인이 없으면 제보 기능 자체가 동작하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final int LIST_LIMIT = 200;
    /** 관리자가 옮길 수 있는 상태. PENDING 으로 되돌리는 것은 의미가 없어 막는다. */
    private static final Set<ReportStatus> TARGET_STATUSES =
            EnumSet.of(ReportStatus.ACTIVE, ReportStatus.REJECTED, ReportStatus.HIDDEN, ReportStatus.DELETED);

    private final ReportRepository reportRepository;
    private final ReportFlagRepository reportFlagRepository;
    private final ReportImageService reportImageService;

    @Transactional(readOnly = true)
    public AdminReportListResponse list(String status) {
        ReportStatus filter = parseListFilter(status);
        List<Report> reports = reportRepository.findForAdmin(filter, PageRequest.of(0, LIST_LIMIT));
        Map<Long, Long> flagCounts = flagCounts(reports);

        return new AdminReportListResponse(reports.stream()
                .map(r -> AdminReportResponse.of(r, flagCounts.getOrDefault(r.getId(), 0L),
                        reportImageService.viewUrl(r.getImageKey())))
                .toList());
    }

    @Transactional(readOnly = true)
    public AdminReportFlagListResponse flags(Long reportId) {
        if (!reportRepository.existsById(reportId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다.");
        }
        return new AdminReportFlagListResponse(reportFlagRepository.findWithUserByReportId(reportId).stream()
                .map(AdminReportFlagListResponse.Item::of)
                .toList());
    }

    @Transactional
    public AdminReportResponse moderate(Long reportId, ReportModerationRequest request) {
        ReportStatus target = parseTarget(request.status());
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다."));

        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        if (target == ReportStatus.REJECTED && note == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "반려할 때는 사유를 적어주세요.");
        }

        report.moderate(target, note, LocalDateTime.now());
        // 반려·삭제된 제보의 사진은 더 보여줄 일이 없어 S3 에서 지운다(개인정보 최소 보관). 숨김(HIDDEN)은 재검토용으로 남긴다.
        if ((target == ReportStatus.REJECTED || target == ReportStatus.DELETED) && report.getImageKey() != null) {
            reportImageService.deleteAfterCommit(report.getImageKey());
            report.clearImage();
        }
        long flagCount = reportFlagRepository.countByReportId(reportId);
        return AdminReportResponse.of(report, flagCount, reportImageService.viewUrl(report.getImageKey()));
    }

    private Map<Long, Long> flagCounts(List<Report> reports) {
        if (reports.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = reports.stream().map(Report::getId).toList();
        return reportFlagRepository.countByReportIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /** null/빈 값 → PENDING, "ALL" → null(DELETED 제외 전부) */
    private ReportStatus parseListFilter(String status) {
        if (status == null || status.isBlank()) {
            return ReportStatus.PENDING;
        }
        if ("ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return ReportStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 상태입니다: " + status);
        }
    }

    private ReportStatus parseTarget(String status) {
        try {
            ReportStatus target = ReportStatus.valueOf(status.toUpperCase());
            if (TARGET_STATUSES.contains(target)) {
                return target;
            }
        } catch (IllegalArgumentException ignored) {
            // 아래에서 400
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "바꿀 수 있는 상태는 ACTIVE, REJECTED, HIDDEN, DELETED 입니다: " + status);
    }
}
