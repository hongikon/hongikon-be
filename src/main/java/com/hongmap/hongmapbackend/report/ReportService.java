package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.report.dto.ReportCreateRequest;
import com.hongmap.hongmapbackend.report.dto.ReportFlagRequest;
import com.hongmap.hongmapbackend.report.dto.ReportFlagResponse;
import com.hongmap.hongmapbackend.report.dto.ReportListResponse;
import com.hongmap.hongmapbackend.report.dto.ReportResponse;
import com.hongmap.hongmapbackend.report.dto.ReportSummaryResponse;
import com.hongmap.hongmapbackend.report.image.ReportImageService;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ReportImageService reportImageService;
    private final ApplicationEventPublisher eventPublisher;

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

        // 사진(최대 3장)은 앱이 presigned URL 로 S3 에 먼저 올렸다. 키 형식·업로드 여부·크기를 확인하고
        // 메타데이터를 지운 사본의 새 키를 받는다(실패 시 400).
        List<String> imageKeys = reportImageService.validateForAttach(request.requestedImageKeys());

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
        report.addImages(imageKeys);

        Report saved = reportRepository.save(report);
        // 관리자 "승인 대기" 알림(AdminAlertDispatcher)은 커밋 뒤 비동기로 나간다.
        eventPublisher.publishEvent(AdminAlertEvent.reportPending(
                saved.getId(), userId, saved.getTitle(), building.getName(), saved.getFloor()));
        return ReportResponse.of(saved, userId, reportImageService.viewUrls(saved.getImageKeys()));
    }

    @Transactional(readOnly = true)
    public ReportListResponse getLiveReports(Long requesterId, Long buildingId) {
        List<Report> reports = reportRepository.findLiveReports(ReportStatus.ACTIVE, LocalDateTime.now(), buildingId);
        List<ReportSummaryResponse> body = reports.stream()
                .map(r -> ReportSummaryResponse.of(r, requesterId, reportImageService.viewUrls(r.getImageKeys())))
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

        List<String> imageKeys = report.getImageKeys();
        reportRepository.delete(report);
        reportImageService.deleteAfterCommit(imageKeys);
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
        // 신고 누적 자동 숨김(이용약관 제8조 4항 — 운영진 확인 전까지 숨김). 마지막 관리자 검토(승인·복원, reviewedAt)
        // 뒤에 들어온 신고만 센다 — 승인된 제보도 새 신고가 임계치에 닿으면 숨기고, 운영진이 신고를 보고 다시 공개한
        // 제보는 그 전 신고로 다시 숨기지 않는다. 검토 전(reviewedAt null)이면 전부 센다.
        if (report.getStatus() == ReportStatus.ACTIVE
                && reportFlagRepository.countByReportIdSince(reportId, report.getReviewedAt()) >= flagThreshold
                && reportRepository.updateStatusIf(reportId, ReportStatus.ACTIVE, ReportStatus.HIDDEN) == 1) {
            // 자동 숨김을 관리자에게 알린다(커밋 뒤 비동기). 조건부 UPDATE라 동시 신고에도 한 번만.
            eventPublisher.publishEvent(AdminAlertEvent.reportFlagged(
                    reportId, userId, report.getTitle(), report.getBuilding().getName(), report.getFloor()));
        }

        return new ReportFlagResponse(flagCount);
    }
}
