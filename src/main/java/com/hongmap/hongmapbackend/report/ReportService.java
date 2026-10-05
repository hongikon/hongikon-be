package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.common.ratelimit.SlidingWindowRateLimiter;
import com.hongmap.hongmapbackend.report.dto.ReportCreateRequest;
import com.hongmap.hongmapbackend.report.dto.ReportFlagRequest;
import com.hongmap.hongmapbackend.report.dto.ReportFlagResponse;
import com.hongmap.hongmapbackend.report.dto.ReportListResponse;
import com.hongmap.hongmapbackend.report.dto.ReportResponse;
import com.hongmap.hongmapbackend.report.dto.ReportSummaryResponse;
import com.hongmap.hongmapbackend.report.image.ReportImageService;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 실시간 제보 서비스.
 * 신고 임계치는 report.flag.threshold, 시작 시각 상한(일)은 report.startsAt.maxDays,
 * 진행 기간 상한(일)은 report.maxDurationDays 설정값으로 관리.
 *
 * 예정 제보: startsAt 을 미래로 잡아 미리 올릴 수 있다(예: 내일 11:00~15:00 붕어빵 트럭).
 * 지도(GET /reports)에는 시작 시각이 된 뒤에야 뜨고, include=upcoming 이면 48시간 안에 시작할 제보를 따로 붙여 준다.
 * 등록 남용 제한(report.create.*)·건물과의 거리 상한(report.building-max-distance-meters)도 여기서 본다.
 */
@Service
public class ReportService {

    /** 지도 목록(GET /reports) 최대 건수 — 최신순. 캠퍼스 하루 제보가 이만큼 쌓일 일은 드물고, 넘으면 오래된 것부터 빠진다. */
    static final int LIVE_LIST_LIMIT = 300;
    /** 층 범위. 앱 층 휠은 지상 1~30층·지하 B1~(같은 휠)이고, 캠퍼스에 지하 10층 아래 건물은 없다. 0층은 없다. */
    static final int MIN_FLOOR = -10;
    static final int MAX_FLOOR = 30;
    /** reports.lat/lng 는 DECIMAL(10,7) — 소수 7자리 넘게 오면 여기서 맞춰 저장한다(DB 마다 반올림·오류가 달라서). */
    private static final int COORDINATE_SCALE = 7;
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    /** 신고 사유: 허위 정보 / 스팸·광고 / 욕설·혐오 등 부적절 / 개인정보 노출 / 기타. */
    private static final List<String> FLAG_REASONS =
            List.of("FALSE_INFO", "SPAM", "INAPPROPRIATE", "PRIVACY", "ETC");
    static final String ALREADY_FLAGGED_MESSAGE = "이미 신고한 제보예요.";

    private final ReportRepository reportRepository;
    private final ReportFlagRepository reportFlagRepository;
    private final com.hongmap.hongmapbackend.comment.ReportCommentCounts reportCommentCounts;
    private final com.hongmap.hongmapbackend.community.ReportCommunityStats reportCommunityStats;
    private final UserRepository userRepository;
    private final BuildingRepository buildingRepository;
    private final ReportImageService reportImageService;
    private final ApplicationEventPublisher eventPublisher;

    /** 시작 시각을 "지금"으로 보낸 앱과 서버 시계가 조금 어긋나도 받아 주는 여유. */
    static final Duration STARTS_AT_PAST_GRACE = Duration.ofMinutes(10);
    /** include=upcoming 으로 함께 내려주는 예정 제보 범위(지금부터). */
    /** 내일모레 아침 행사도 미리 보이게 이틀(48시간) 앞까지(10-05 요청, 처음엔 24시간). */
    static final Duration UPCOMING_WINDOW = Duration.ofHours(48);

    @Value("${report.startsAt.maxDays}")
    private long startsAtMaxDays;

    @Value("${report.maxDurationDays}")
    private long maxDurationDays;

    @Value("${report.flag.threshold}")
    private long flagThreshold;

    /** 한 사람이 승인 대기(PENDING)로 동시에 가질 수 있는 제보 수. 승인·반려되면 다시 올릴 수 있다. */
    private final long maxPendingPerUser;
    /** 제보 좌표와 고른 건물 중심 사이 거리 상한(m). 앱은 200m 안의 가장 가까운 건물을 고르므로 좌표 차이를 감안해 여유를 둔다. */
    private final double buildingMaxDistanceMeters;
    /** 사용자별 등록 횟수 제한(1시간). 서버 1대 메모리 기준. */
    private final SlidingWindowRateLimiter createLimiter;

    public ReportService(ReportRepository reportRepository, ReportFlagRepository reportFlagRepository,
                         com.hongmap.hongmapbackend.comment.ReportCommentCounts reportCommentCounts,
                         com.hongmap.hongmapbackend.community.ReportCommunityStats reportCommunityStats,
                         UserRepository userRepository, BuildingRepository buildingRepository,
                         ReportImageService reportImageService, ApplicationEventPublisher eventPublisher,
                         @Value("${report.create.limit-per-hour:5}") int createLimitPerHour,
                         @Value("${report.create.max-pending:3}") long maxPendingPerUser,
                         @Value("${report.building-max-distance-meters:300}") double buildingMaxDistanceMeters) {
        this.reportRepository = reportRepository;
        this.reportFlagRepository = reportFlagRepository;
        this.reportCommentCounts = reportCommentCounts;
        this.reportCommunityStats = reportCommunityStats;
        this.userRepository = userRepository;
        this.buildingRepository = buildingRepository;
        this.reportImageService = reportImageService;
        this.eventPublisher = eventPublisher;
        this.maxPendingPerUser = maxPendingPerUser;
        this.buildingMaxDistanceMeters = buildingMaxDistanceMeters;
        this.createLimiter = new SlidingWindowRateLimiter(createLimitPerHour, Duration.ofHours(1), Clock.systemUTC());
    }

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

        if (request.floor() == 0) {
            // 범위(-10~30)는 DTO 가 본다. 0층은 없는 층이라(B1=-1, 1F=1) 여기서 거른다.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "층을 다시 골라 주세요.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        Building building = buildingRepository.findById(request.buildingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 건물입니다."));
        BigDecimal lat = request.lat().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal lng = request.lng().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
        // 앱을 거치지 않은 요청이 엉뚱한 곳(캠퍼스 밖·다른 건물)의 좌표에 건물만 붙여 올리는 걸 막는다.
        // 건물 좌표는 DB(buildings) 값이고 앱은 자체 건물 좌표로 200m 안을 고르므로, 둘이 어긋나도 걸리지 않게 300m 로 둔다.
        if (distanceMeters(lat, lng, building.getLatitude(), building.getLongitude()) > buildingMaxDistanceMeters) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "제보 위치가 고른 건물에서 너무 멀어요. 건물 가까이로 옮겨 주세요.");
        }

        // 도배 방지 ① 승인 대기 수 — DB 로 센다(재시작·서버 여러 대에도 정확). 동시에 두 건이 오면 한 건 넘칠 수 있지만
        // 도배 방지엔 충분하다. 아무것도 기록하지 않으니 사진 정리(S3 사본) 전에 먼저 본다.
        if (reportRepository.countByUser_IdAndStatus(userId, ReportStatus.PENDING) >= maxPendingPerUser) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "승인을 기다리는 제보가 " + maxPendingPerUser + "건 있어요. 검토가 끝난 뒤 다시 올려 주세요.");
        }

        // 사진(최대 3장)은 앱이 presigned URL 로 S3 에 먼저 올렸다. 키 형식·업로드 여부·크기를 확인하고
        // 메타데이터를 지운 사본의 새 키를 받는다(실패 시 400).
        List<String> imageKeys = reportImageService.validateForAttach(request.requestedImageKeys());

        // 도배 방지 ② 1시간 등록 횟수 — 검증(사진 포함)을 다 통과한 요청만 센다. 사진이 잘못돼 400 받고 다시 고르는 사용자가
        // 한도를 깎이지 않게. 여기서 429 로 롤백되면 위에서 만든 사진 사본은 ReportImageService 가 롤백 시 지운다.
        if (!createLimiter.tryAcquire("user:" + userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "제보를 너무 자주 올리고 있어요. 잠시 후 다시 시도해 주세요.");
        }

        Report report = Report.builder()
                .user(user)
                .building(building)
                .floor(request.floor())
                .lat(lat)
                .lng(lng)
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

    /**
     * 시작·종료 시각 규칙(서버 시각 UTC 기준). 앱도 같은 규칙으로 막지만, 구버전 앱·직접 호출을 위해 여기서 확정한다.
     * <ul>
     *   <li>startsAt: 지금(10분 여유) ~ 지금 + startsAtMaxDays일</li>
     *   <li>endsAt: startsAt 보다 뒤, startsAt + maxDurationDays일 이내(여러 날 행사 가능) (지났는지는 @Future 가 본다)</li>
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
        if (endsAt.isAfter(startsAt.plusDays(maxDurationDays))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "진행 기간은 최대 " + maxDurationDays + "일까지 정할 수 있어요.");
        }
    }

    /**
     * 지도용 목록. 기본은 지금 진행 중(startsAt ≤ now ≤ endsAt)인 ACTIVE 제보만.
     * includeUpcoming 이면 48시간 안에 시작할 ACTIVE 제보를 시작 시각 순으로 뒤에 붙인다(앱의 "예정" 표시용 —
     * 항목의 startsAt 이 지금보다 뒤면 예정이다). 시작 전 제보는 기본 목록에 절대 섞이지 않는다.
     */
    @Transactional(readOnly = true)
    public ReportListResponse getLiveReports(Long requesterId, Long buildingId, boolean includeUpcoming) {
        LocalDateTime now = LocalDateTime.now();
        List<Report> reports = new ArrayList<>(reportRepository.findLiveReports(ReportStatus.ACTIVE, now, buildingId,
                PageRequest.of(0, LIVE_LIST_LIMIT)));
        if (includeUpcoming) {
            reports.addAll(reportRepository.findUpcomingReports(
                    ReportStatus.ACTIVE, now, now.plus(UPCOMING_WINDOW), buildingId));
        }
        List<ReportSummaryResponse> body = reports.stream()
                .map(r -> ReportSummaryResponse.of(r, requesterId, reportImageService.viewUrls(r.getImageKeys())))
                .toList();
        return new ReportListResponse(reportCommunityStats.attach(reportCommentCounts.attach(body), requesterId, null));
    }

    @Transactional
    public void delete(Long userId, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다."));

        if (!report.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 제보만 삭제할 수 있습니다.");
        }
        // 신고 누적 등으로 숨겨진 제보는 운영진 검토가 끝날 때까지 작성자가 지울 수 없다 — 검토 전에 지우면
        // 신고 기록까지 사라져 악성 이용자 제재 근거가 남지 않는다(약관 제8·10조, App Store 1.2).
        // "검토 전" = 마지막 검토(reviewedAt) 뒤에 들어온 신고가 있다. 운영진이 직접 숨긴(검토가 끝난) 제보는 지울 수 있다.
        if (report.getStatus() == ReportStatus.HIDDEN
                && reportFlagRepository.existsByReportIdCreatedAfter(reportId, report.getReviewedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신고로 검토 중인 제보는 운영진 검토가 끝난 뒤에 지울 수 있어요.");
        }

        List<String> imageKeys = report.getImageKeys();
        reportFlagRepository.deleteAllByReportId(reportId);
        reportRepository.delete(report);
        reportImageService.deleteAfterCommit(imageKeys);
    }

    @Transactional
    public ReportFlagResponse flag(Long userId, Long reportId, ReportFlagRequest request) {
        if (!FLAG_REASONS.contains(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 신고 사유입니다: " + request.reason());
        }

        // 지도에 보이는(ACTIVE) 제보만 신고할 수 있다. 승인 대기·반려·숨김·삭제 제보는 일반 사용자에게 보이지 않으므로
        // 있는지 없는지도 드러내지 않게 같은 404 로 답한다(id 를 넣어 보며 비공개 제보를 찾는 것 방지).
        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getStatus() == ReportStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다."));

        if (report.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내가 올린 제보는 신고할 수 없어요.");
        }
        if (reportFlagRepository.existsByReportIdAndUserId(reportId, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_FLAGGED_MESSAGE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        ReportFlag flag = ReportFlag.builder()
                .report(report)
                .user(user)
                .reason(request.reason())
                .build();
        try {
            // 바로 INSERT 해서 uq_flag(report_id, user_id) 위반을 여기서 받는다. 같은 신고가 동시에 두 번 오면(더블 탭)
            // 위 exists 확인을 둘 다 통과하는데, 늦은 쪽을 500 이 아니라 "이미 신고함" 409 로 돌려준다(앱은 409 를 신고 완료로 표시).
            reportFlagRepository.saveAndFlush(flag);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_FLAGGED_MESSAGE);
        }
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

        // 전체 신고 수는 돌려주지 않는다 — 앱은 쓰지 않고, 다른 사람이 몇 명 신고했는지는 신고자에게 알릴 정보가 아니다.
        return new ReportFlagResponse(true);
    }

    /** 두 좌표 사이 거리(m, 하버사인). 캠퍼스 규모에선 오차가 1m 미만이다. */
    static double distanceMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
