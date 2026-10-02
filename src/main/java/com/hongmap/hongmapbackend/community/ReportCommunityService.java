package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.community.dto.AuthorNotifyResponse;
import com.hongmap.hongmapbackend.community.dto.FireResponse;
import com.hongmap.hongmapbackend.community.dto.FollowResponse;
import com.hongmap.hongmapbackend.community.dto.ViewResponse;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 제보 커뮤니티: 🔥(불), 관심 제보, 작성자 "이 제보 알림", 조회 수.
 *
 * <ul>
 *   <li>🔥: 로그인 필수, 남의 공개(ACTIVE) 제보에만, 한 사람 한 번(끄면 행 삭제). 끝난 제보는 409.
 *       최근 report.hot.window-minutes(60) 안의 🔥가 report.hot.threshold(5) 이상이면 HOT.
 *       🔥 수가 10·50·100 을 처음 넘으면 작성자에게 한 번씩 알림(작성자 알림·"내 제보 결과 알림" 설정을 따름).</li>
 *   <li>관심: 로그인 필수, 남의 공개 제보에만, 한 사람 최대 report.follow.max-per-user(100)개. 끝난 제보는 409.</li>
 *   <li>🔥·관심·👍 누르기는 합쳐서 1분 report.reaction.rate-per-minute(20)번(429).</li>
 *   <li>조회 수: 게스트도. 로그인 사용자는 계정, 아니면 X-Install-Id(앱 설치마다 만든 임의 값)로 하루(KST) 한 번만 센다.
 *       원래 id 는 저장하지 않고 날짜를 섞은 HMAC 만 2일 보관한다. IP 는 쓰지 않는다.</li>
 * </ul>
 * 누가 눌렀는지·보았는지는 어떤 응답에도 싣지 않는다(수와 "내가 눌렀는지"만).
 */
@Service
public class ReportCommunityService {

    static final int[] FIRE_MILESTONES = {100, 50, 10};
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern INSTALL_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ReportReactionRepository reactionRepository;
    private final ReportFollowRepository followRepository;
    private final ReportEngagementRepository engagementRepository;
    private final CommunityActionLimiter limiter;
    private final ApplicationEventPublisher eventPublisher;
    private final long hotThreshold;
    private final long hotWindowMinutes;
    private final long maxFollowsPerUser;
    private final byte[] viewKeySecret;

    public ReportCommunityService(ReportRepository reportRepository,
                                  UserRepository userRepository,
                                  ReportReactionRepository reactionRepository,
                                  ReportFollowRepository followRepository,
                                  ReportEngagementRepository engagementRepository,
                                  CommunityActionLimiter limiter,
                                  ApplicationEventPublisher eventPublisher,
                                  @Value("${report.hot.threshold:5}") long hotThreshold,
                                  @Value("${report.hot.window-minutes:60}") long hotWindowMinutes,
                                  @Value("${report.follow.max-per-user:100}") long maxFollowsPerUser,
                                  @Value("${report.view.key-secret:${jwt.secret:}}") String viewKeySecret) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.reactionRepository = reactionRepository;
        this.followRepository = followRepository;
        this.engagementRepository = engagementRepository;
        this.limiter = limiter;
        this.eventPublisher = eventPublisher;
        this.hotThreshold = hotThreshold;
        this.hotWindowMinutes = hotWindowMinutes;
        this.maxFollowsPerUser = maxFollowsPerUser;
        if (viewKeySecret == null || viewKeySecret.isBlank()) {
            // 키가 없으면 기동마다 새 키 — 재시작 뒤 같은 날 한 번 더 셀 수 있을 뿐이다.
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.viewKeySecret = random;
        } else {
            this.viewKeySecret = ("report-view:" + viewKeySecret).getBytes(StandardCharsets.UTF_8);
        }
    }

    public long hotThreshold() {
        return hotThreshold;
    }

    /** HOT 을 세는 시작 시각(지금 - window). */
    public LocalDateTime hotSince() {
        return LocalDateTime.now().minusMinutes(hotWindowMinutes);
    }

    // ---------- 🔥 ----------

    @Transactional
    public FireResponse setFire(Long userId, Long reportId, boolean on) {
        Report report = requireOpenReport(reportId);
        if (report.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내 제보에는 🔥를 누를 수 없어요.");
        }
        acquire(userId);
        boolean added = false;
        if (on) {
            if (!reactionRepository.existsByReport_IdAndUser_Id(reportId, userId)) {
                try {
                    reactionRepository.saveAndFlush(new ReportReaction(report, requireUser(userId)));
                    added = true;
                } catch (DataIntegrityViolationException e) {
                    // 같은 사람이 동시에 두 번 눌렀다 — 이미 눌린 상태로 본다.
                }
            }
        } else {
            reactionRepository.deleteMine(reportId, userId);
        }
        long fireCount = reactionRepository.countByReport_Id(reportId);
        long recent = reactionRepository.countByReport_IdAndCreatedAtGreaterThanEqual(reportId, hotSince());
        if (added) {
            notifyMilestone(report, fireCount);
        }
        return new FireResponse(on, fireCount, recent, recent >= hotThreshold);
    }

    private void notifyMilestone(Report report, long fireCount) {
        for (int milestone : FIRE_MILESTONES) {
            if (fireCount >= milestone) {
                engagementRepository.ensureRow(report.getId());
                if (engagementRepository.claimFireMilestone(report.getId(), milestone) == 1) {
                    eventPublisher.publishEvent(new ReportFireMilestoneEvent(
                            report.getId(), report.getUser().getId(), report.getTitle(), milestone));
                }
                return; // 가장 큰 이정표 하나만(10 → 50 을 건너뛰어 넘겨도 50 한 번)
            }
        }
    }

    // ---------- 관심 ----------

    @Transactional
    public FollowResponse setFollow(Long userId, Long reportId, boolean on) {
        Report report = requireOpenReport(reportId);
        if (report.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내 제보는 관심 등록하지 않아도 알림이 와요.");
        }
        acquire(userId);
        if (on) {
            if (!followRepository.existsByReport_IdAndUser_Id(reportId, userId)) {
                if (followRepository.countByUser_Id(userId) >= maxFollowsPerUser) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "관심 제보는 " + maxFollowsPerUser + "개까지 등록할 수 있어요.");
                }
                try {
                    followRepository.saveAndFlush(new ReportFollow(report, requireUser(userId)));
                } catch (DataIntegrityViolationException e) {
                    // 동시에 두 번 — 이미 등록된 상태로 본다.
                }
            }
        } else {
            followRepository.deleteMine(reportId, userId);
        }
        return new FollowResponse(on);
    }

    // ---------- 작성자 알림 ----------

    @Transactional
    public AuthorNotifyResponse setAuthorNotify(Long userId, Long reportId, boolean enabled) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다."));
        if (!report.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내 제보의 알림만 바꿀 수 있어요.");
        }
        engagementRepository.ensureRow(reportId);
        engagementRepository.updateAuthorNotify(reportId, enabled);
        return new AuthorNotifyResponse(enabled);
    }

    /** 작성자 알림이 켜져 있는지(행이 없으면 켜짐). 댓글·🔥 이정표 푸시가 쓴다. */
    @Transactional(readOnly = true)
    public boolean isAuthorNotifyEnabled(Long reportId) {
        return engagementRepository.findById(reportId)
                .map(ReportEngagement::isAuthorNotifyEnabled)
                .orElse(ReportEngagement.DEFAULT_AUTHOR_NOTIFY_ENABLED);
    }

    // ---------- 조회 수 ----------

    @Transactional
    public ViewResponse recordView(Long userId, String installId, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다."));
        if (report.getStatus() != ReportStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다.");
        }
        String viewer = userId != null ? "u:" + userId
                : (installId != null && INSTALL_ID.matcher(installId).matches() ? "i:" + installId : null);
        boolean counted = false;
        if (viewer != null) {
            LocalDate today = LocalDate.now(KST);
            if (engagementRepository.insertViewMark(reportId, viewerKey(today, viewer), today) == 1) {
                engagementRepository.ensureRow(reportId);
                engagementRepository.incrementViews(reportId);
                counted = true;
            }
        }
        long viewCount = engagementRepository.findById(reportId).map(ReportEngagement::getViewCount).orElse(0L);
        return new ViewResponse(viewCount, counted);
    }

    /** HMAC-SHA256(비밀키, 날짜|viewer) 앞 16바이트 hex(32자). */
    String viewerKey(LocalDate date, String viewer) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(viewKeySecret, "HmacSHA256"));
            byte[] digest = mac.doFinal((date + "|" + viewer).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- 내부 ----------

    /** 공개 중(ACTIVE)이고 끝나지 않은 제보. 공개가 아니면 404, 끝났으면 409. */
    private Report requireOpenReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다."));
        if (report.getStatus() != ReportStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다.");
        }
        if (report.getEndsAt() != null && report.getEndsAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "끝난 제보예요.");
        }
        return report;
    }

    private void acquire(Long userId) {
        if (!limiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "너무 자주 누르고 있어요. 잠시 뒤에 다시 시도해 주세요.");
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));
    }
}
