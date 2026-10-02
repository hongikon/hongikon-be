package com.hongmap.hongmapbackend.report;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 실시간 제보 서비스.
 * 신고 임계치는 report.flag.threshold, 시작 시각 상한(일)은 report.startsAt.maxDays,
 * 진행 시간 상한(시간)은 report.maxDurationHours 설정값으로 관리.
 *
 * 예정 제보: startsAt 을 미래로 잡아 미리 올릴 수 있다(예: 내일 11:00~15:00 붕어빵 트럭).
 * 지도(GET /reports)에는 시작 시각이 된 뒤에야 뜨고, include=upcoming 이면 24시간 안에 시작할 제보를 따로 붙여 준다.
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

    /** 시작 시각을 "지금"으로 보낸 앱과 서버 시계가 조금 어긋나도 받아 주는 여유. */
    static final Duration STARTS_AT_PAST_GRACE = Duration.ofMinutes(10);
    /** include=upcoming 으로 함께 내려주는 예정 제보 범위(지금부터). */
    static final Duration UPCOMING_WINDOW = Duration.ofHours(24);

    @Value("${report.startsAt.maxDays}")
    private long startsAtMaxDays;

    @Value("${report.maxDurationHours}")
    private long maxDurationHours;

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
        validateSchedule(request.startsAt(), request.endsAt(), LocalDateTime.now());

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
        return ReportResponse.of(saved, userId, reportImageService.viewUrls(saved.getImageKeys()));
    }

    /**
     * 시작·종료 시각 규칙(서버 시각 UTC 기준). 앱도 같은 규칙으로 막지만, 구버전 앱·직접 호출을 위해 여기서 확정한다.
     * <ul>
     *   <li>startsAt: 지금(10분 여유) ~ 지금 + startsAtMaxDays일</li>
     *   <li>endsAt: startsAt 보다 뒤, startsAt + maxDurationHours시간 이내 (지났는지는 @Future 가 본다)</li>
     * </ul>
     */
    void validateSchedule(LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
        if (startsAt.isBefore(now.minus(STARTS_AT_PAST_GRACE))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "시작 시각이 이미 지났어요. 지금 또는 이후 시각을 골라 주세요.");
        }
        if (startsAt.isAfter(now.plusDays(startsAtMaxDays))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "시작 시각은 오늘부터 " + startsAtMaxDays + "일 안으로 골라 주세요.");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료 시각은 시작 시각보다 뒤여야 해요.");
        }
        if (endsAt.isAfter(startsAt.plusHours(maxDurationHours))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "진행 시간은 최대 " + maxDurationHours + "시간까지 정할 수 있어요.");
        }
    }

    /**
     * 지도용 목록. 기본은 지금 진행 중(startsAt ≤ now ≤ endsAt)인 ACTIVE 제보만.
     * includeUpcoming 이면 24시간 안에 시작할 ACTIVE 제보를 시작 시각 순으로 뒤에 붙인다(앱의 "예정" 표시용 —
     * 항목의 startsAt 이 지금보다 뒤면 예정이다). 시작 전 제보는 기본 목록에 절대 섞이지 않는다.
     */
    @Transactional(readOnly = true)
    public ReportListResponse getLiveReports(Long requesterId, Long buildingId, boolean includeUpcoming) {
        LocalDateTime now = LocalDateTime.now();
        List<Report> reports = new ArrayList<>(reportRepository.findLiveReports(ReportStatus.ACTIVE, now, buildingId));
        if (includeUpcoming) {
            reports.addAll(reportRepository.findUpcomingReports(
                    ReportStatus.ACTIVE, now, now.plus(UPCOMING_WINDOW), buildingId));
        }
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
        // 관리자가 이미 검토해 공개를 유지한 제보는 신고가 더 쌓여도 자동으로 숨기지 않는다.
        if (flagCount >= flagThreshold && report.getStatus() == ReportStatus.ACTIVE
                && report.getReviewedAt() == null) {
            reportRepository.updateStatus(reportId, ReportStatus.HIDDEN);
        }

        return new ReportFlagResponse(flagCount);
    }
}
