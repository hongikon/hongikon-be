package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.comment.dto.AdminCommentListResponse;
import com.hongmap.hongmapbackend.comment.dto.AdminCommentResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentFlagResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentResponse;
import com.hongmap.hongmapbackend.common.dto.PageResponse;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 제보 댓글(사용자 생성 콘텐츠 — App Store 1.2, 이용약관 게시물).
 *
 * <ul>
 *   <li>읽기: 누구나(게스트 포함). 지도에 공개된(ACTIVE) 제보의 VISIBLE 댓글만. 다른 상태의 제보는 404.</li>
 *   <li>쓰기: 로그인 필수, 앞뒤 공백 제거 후 1~200자, 끝나지 않은 ACTIVE 제보에만. 1분 5개·하루 50개(429).
 *       정지 회원은 #13 의 SuspendedUserInterceptor 가 403 으로 막는다(ReportCommentWebConfig).</li>
 *   <li>사후 검토: 바로 공개, 신고가 report.comment.flag-threshold(기본 3)개 쌓이면 자동 숨김.
 *       관리자 검토(reviewedAt) 뒤의 신고만 센다 — 복원한 댓글을 옛 신고로 다시 숨기지 않는다.</li>
 *   <li>삭제: 작성자 본인은 DELETED 로 바꾼다(행은 신고 검토·빈도 제한 근거로 남고 제보·탈퇴와 함께 DB 에서 지워진다).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCommentService {

    /** 제보 신고와 같은 사유. */
    static final List<String> FLAG_REASONS = List.of("FALSE_INFO", "SPAM", "INAPPROPRIATE", "PRIVACY", "ETC");
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;
    private static final Set<ReportCommentStatus> ADMIN_TARGETS = EnumSet.allOf(ReportCommentStatus.class);

    private final ReportCommentRepository commentRepository;
    private final ReportCommentFlagRepository flagRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final CommentAuthorKeys authorKeys;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${report.comment.flag-threshold:3}")
    private long flagThreshold;

    @Value("${report.comment.rate-per-minute:5}")
    private long ratePerMinute;

    @Value("${report.comment.rate-per-day:50}")
    private long ratePerDay;

    // ---------- 공개 ----------

    /** order: "oldest"(기본, 오래된 순) / "latest"(최신 순 — 시트 미리보기용). */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(Long requesterId, Long reportId, int page, int size, String order) {
        requireVisibleReport(reportId);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Sort sort = "latest".equalsIgnoreCase(order) ? Sort.by(Sort.Direction.DESC, "id") : Sort.by("id");
        var result = commentRepository.findByReport_IdAndStatus(
                reportId, ReportCommentStatus.VISIBLE, PageRequest.of(Math.max(page, 0), safeSize, sort));
        return PageResponse.of(result.map(c -> toResponse(c, requesterId)));
    }

    @Transactional
    public CommentResponse create(Long userId, Long reportId, String rawContent) {
        String content = rawContent == null ? "" : rawContent.strip();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해 주세요.");
        }
        if (content.length() > ReportComment.MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "댓글은 " + ReportComment.MAX_LENGTH + "자까지 쓸 수 있어요.");
        }
        Report report = requireVisibleReport(reportId);
        LocalDateTime now = LocalDateTime.now();
        if (report.getEndsAt() != null && report.getEndsAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "끝난 제보에는 댓글을 달 수 없어요.");
        }
        if (commentRepository.countByUser_IdAndCreatedAtAfter(userId, now.minusMinutes(1)) >= ratePerMinute
                || commentRepository.countByUser_IdAndCreatedAtAfter(userId, now.minusDays(1)) >= ratePerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "댓글을 너무 자주 달고 있어요. 잠시 뒤에 다시 시도해 주세요.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        ReportComment saved = commentRepository.save(new ReportComment(report, user, content));
        // 제보 작성자에게 "내 제보에 댓글이 달렸어요"(커밋 뒤 비동기, 본인 댓글·설정 끔·10분 묶음은 디스패처가 거른다).
        eventPublisher.publishEvent(new ReportCommentCreatedEvent(
                reportId, report.getUser().getId(), userId, report.getTitle(), content));
        return toResponse(saved, userId);
    }

    @Transactional
    public void deleteOwn(Long userId, Long reportId, Long commentId) {
        ReportComment comment = requireComment(reportId, commentId);
        if (!comment.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내가 쓴 댓글만 지울 수 있어요.");
        }
        if (comment.getStatus() != ReportCommentStatus.DELETED) {
            comment.deleteByAuthor();
        }
    }

    @Transactional
    public CommentFlagResponse flag(Long userId, Long reportId, Long commentId, String reason) {
        if (reason == null || !FLAG_REASONS.contains(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 신고 사유입니다: " + reason);
        }
        ReportComment comment = requireComment(reportId, commentId);
        if (comment.getStatus() != ReportCommentStatus.VISIBLE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다.");
        }
        if (comment.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내 댓글은 신고할 수 없어요.");
        }
        if (flagRepository.existsByComment_IdAndUser_Id(commentId, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 댓글이에요.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));
        try {
            flagRepository.saveAndFlush(new ReportCommentFlag(comment, user, reason));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 댓글이에요.");
        }

        long sinceReview = comment.getReviewedAt() == null
                ? flagRepository.countByComment_Id(commentId)
                : flagRepository.countByComment_IdAndCreatedAtAfter(commentId, comment.getReviewedAt());
        boolean hidden = sinceReview >= flagThreshold && commentRepository.hideIfVisible(commentId) == 1;
        if (hidden) {
            // TODO(#14 머지 뒤): AdminAlertEvent 로 관리자에게 "[관리] 신고 누적으로 댓글 자동 숨김" 알림.
            log.info("댓글 신고 누적 자동 숨김: reportId={}, commentId={}, flags={}", reportId, commentId, sinceReview);
        }
        return new CommentFlagResponse(flagRepository.countByComment_Id(commentId), hidden);
    }

    // ---------- 관리자 ----------

    @Transactional(readOnly = true)
    public AdminCommentListResponse adminList(Long reportId) {
        if (!reportRepository.existsById(reportId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다.");
        }
        List<ReportComment> comments = commentRepository.findAllForAdmin(reportId);
        Map<Long, Map<String, Long>> reasons = flagReasons(comments);
        return new AdminCommentListResponse(comments.stream()
                .map(c -> AdminCommentResponse.of(c, reasons.get(c.getId())))
                .toList());
    }

    @Transactional
    public AdminCommentResponse moderate(Long commentId, String status) {
        ReportCommentStatus target = parseTarget(status);
        ReportComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."));
        comment.moderate(target, LocalDateTime.now());
        return AdminCommentResponse.of(comment, flagReasons(List.of(comment)).get(commentId));
    }

    // ---------- 내부 ----------

    private CommentResponse toResponse(ReportComment comment, Long requesterId) {
        return CommentResponse.of(comment, requesterId, authorKeys.of(comment.getUser().getId()));
    }

    /** 지도에 공개된 제보만. PENDING·REJECTED·HIDDEN·DELETED 는 존재를 드러내지 않게 404. */
    private Report requireVisibleReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다."));
        if (report.getStatus() != ReportStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 제보입니다.");
        }
        return report;
    }

    private ReportComment requireComment(Long reportId, Long commentId) {
        ReportComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."));
        if (!comment.getReport().getId().equals(reportId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다.");
        }
        return comment;
    }

    private Map<Long, Map<String, Long>> flagReasons(List<ReportComment> comments) {
        if (comments.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, Long>> result = new HashMap<>();
        for (Object[] row : flagRepository.countByCommentIdsAndReason(comments.stream().map(ReportComment::getId).toList())) {
            result.computeIfAbsent((Long) row[0], k -> new LinkedHashMap<>()).put((String) row[1], (Long) row[2]);
        }
        return result;
    }

    private static ReportCommentStatus parseTarget(String status) {
        try {
            ReportCommentStatus target = ReportCommentStatus.valueOf(status.trim().toUpperCase());
            if (ADMIN_TARGETS.contains(target)) {
                return target;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // 아래에서 400
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "바꿀 수 있는 상태는 VISIBLE, HIDDEN, DELETED 입니다: " + status);
    }
}
