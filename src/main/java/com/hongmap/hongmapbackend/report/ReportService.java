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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 실시간 제보 서비스.
 * 신고 임계치(3건)는 REPORT_HIDE_THRESHOLD 상수로 관리 — 석훈님 확인 대기 중인 값이라 바뀔 수 있음.
 * ends_at 상한(12시간)은 REPORT_MAX_DURATION 상수로 관리 — 마찬가지로 가안.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final long REPORT_HIDE_THRESHOLD = 3;
    private static final Duration REPORT_MAX_DURATION = Duration.ofHours(12);

    private final ReportRepository reportRepository;
    private final ReportFlagRepository reportFlagRepository;
    private final UserRepository userRepository;
    private final BuildingRepository buildingRepository;

    @Transactional
    public ReportResponse create(Long userId, ReportCreateRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endsAt은 startsAt보다 이후여야 합니다.");
        }
        if (Duration.between(request.startsAt(), request.endsAt()).compareTo(REPORT_MAX_DURATION) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제보는 최대 12시간까지만 등록할 수 있습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        Building building = null;
        if (request.buildingId() != null) {
            building = buildingRepository.findById(request.buildingId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 건물입니다."));
        }

        Report report = Report.builder()
                .user(user)
                .building(building)
                .floor(request.floor())
                .lat(request.lat())
                .lng(request.lng())
                .category(request.category())
                .title(request.title())
                .content(request.content())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .status("ACTIVE")
                .build();

        Report saved = reportRepository.save(report);
        return ReportResponse.of(saved, userId);
    }

    @Transactional(readOnly = true)
    public ReportListResponse getLiveReports(Long requesterId, Long buildingId) {
        List<Report> reports = reportRepository.findLiveReports(LocalDateTime.now(), buildingId);
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
        if (flagCount >= REPORT_HIDE_THRESHOLD && "ACTIVE".equals(report.getStatus())) {
            // Report는 @Setter가 없으므로(불변 지향) 상태 변경은 별도 update 메서드나
            // JPQL bulk update로 처리 필요. 우선 리포지토리 레벨에서 처리하도록 TODO.
            reportRepository.updateStatus(reportId, "HIDDEN");
        }

        return new ReportFlagResponse(flagCount);
    }
}
