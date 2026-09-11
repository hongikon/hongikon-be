package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.report.dto.ReportCreateRequest;
import com.hongmap.hongmapbackend.report.dto.ReportFlagRequest;
import com.hongmap.hongmapbackend.report.dto.ReportFlagResponse;
import com.hongmap.hongmapbackend.report.dto.ReportListResponse;
import com.hongmap.hongmapbackend.report.dto.ReportResponse;
import com.hongmap.hongmapbackend.report.dto.ReportSummaryResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 실시간 제보 서비스.
 * 신고 임계치는 report.flag.threshold, endsAt 상한(일)은 report.endsAt.maxDays 설정값으로 관리.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 신고 사유 예시값. */
    private static final List<String> FLAG_REASONS =
            List.of("FALSE_INFO", "SPAM", "INAPPROPRIATE", "ETC");

    private final ReportRepository reportRepository;
    private final ReportFlagRepository reportFlagRepository;
    private final UserRepository userRepository;
    private final BuildingRepository buildingRepository;

    @Value("${report.endsAt.maxDays}")
    private long endsAtMaxDays;

    @Value("${report.flag.threshold}")
    private long flagThreshold;

    @Transactional
    public ReportResponse create(Long userId, ReportCreateRequest request) {
        ReportCategory category;
        try {
            category = ReportCategory.valueOf(request.category());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다: " + request.category());
        }
        if (category != ReportCategory.ETC && request.customCategoryLabel() != null
                && !request.customCategoryLabel().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customCategoryLabel은 ETC 카테고리에서만 사용할 수 있습니다.");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endsAt은 startsAt보다 이후여야 합니다.");
        }
        LocalDateTime maxEndsAt = LocalDateTime.now().plusDays(endsAtMaxDays);
        if (request.endsAt().isAfter(maxEndsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "endsAt은 현재로부터 최대 " + endsAtMaxDays + "일 이내여야 합니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        Building building = buildingRepository.findById(request.buildingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 건물입니다."));

        Report report = Report.builder()
                .user(user)
                .building(building)
                .floor(request.floor())
                .lat(request.lat())
                .lng(request.lng())
                .category(category)
                .customCategoryLabel(category == ReportCategory.ETC ? request.customCategoryLabel() : null)
                .title(request.title())
                .content(request.content())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .status(ReportStatus.PENDING)
                .build();

        Report saved = reportRepository.save(report);
        return ReportResponse.of(saved, userId);
    }

    @Transactional(readOnly = true)
    public ReportListResponse getLiveReports(Long requesterId, Long buildingId) {
        List<Report> reports = reportRepository.findLiveReports(ReportStatus.ACTIVE, LocalDateTime.now(), buildingId);
        List<ReportSummaryResponse> body = reports.stream()
                .map(r -> ReportSummaryResponse.of(r, requesterId))
                .toList();
        return new ReportListResponse(body);
    }

    @Transactional
    public void delete(Long userId, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다."));

        if (!report.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 제보만 삭제할 수 있습니다.");
        }

        reportRepository.delete(report);
    }

    @Transactional
    public ReportFlagResponse flag(Long userId, Long reportId, ReportFlagRequest request) {
        if (!FLAG_REASONS.contains(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 신고 사유입니다: " + request.reason());
        }

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다."));

        if (reportFlagRepository.existsByReportIdAndUserId(reportId, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 제보입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        ReportFlag flag = ReportFlag.builder()
                .report(report)
                .user(user)
                .reason(request.reason())
                .build();
        reportFlagRepository.save(flag);

        long flagCount = reportFlagRepository.countByReportId(reportId);
        if (flagCount >= flagThreshold && report.getStatus() == ReportStatus.ACTIVE) {
            reportRepository.updateStatus(reportId, ReportStatus.HIDDEN);
        }

        return new ReportFlagResponse(flagCount);
    }
}
