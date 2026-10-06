package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.comment.dto.AdminCommentListResponse;
import com.hongmap.hongmapbackend.comment.dto.AdminCommentResponse;
import com.hongmap.hongmapbackend.comment.dto.AdminFlaggedCommentListResponse;
import com.hongmap.hongmapbackend.comment.dto.AdminFlaggedCommentResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentFlagResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentLikeResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentListResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentResponse;
import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import com.hongmap.hongmapbackend.common.dto.PageResponse;
import com.hongmap.hongmapbackend.common.moderation.ContentFilter;
import com.hongmap.hongmapbackend.common.moderation.ContentViolation;
import com.hongmap.hongmapbackend.community.CommunityActionLimiter;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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
 *       링크·연락처·욕설은 {@link ContentFilter} 가 올리기 전에 400 으로 막는다(사후 검토라 올리는 순간 한 번 거른다).
 *       정지 회원은 #13 의 SuspendedUserInterceptor 가 403 으로 막는다(ReportCommentWebConfig).</li>
 *   <li>사후 검토: 바로 공개, 신고가 report.comment.flag-threshold(기본 3)개 쌓이면 자동 숨김.
 *       관리자 검토(reviewedAt) 뒤의 신고만 센다 — 복원한 댓글을 옛 신고로 다시 숨기지 않는다.
 *       검토 뒤 첫 신고와 자동 숨김은 관리자에게 알리고(ADMIN_COMMENT_FLAGGED), 숨김·삭제는 작성자에게 사유와 함께 알린다
 *       (COMMENT_MODERATED, 이용약관 제10조). 신고는 10분 10번까지(CommentFlagLimiter, 429).</li>
 *   <li>답글: 한 단계만. 답글에 답하면 같은 최상위 댓글에 붙는다. 목록은 최상위 댓글 단위 페이지 + 답글 앞쪽 3개·답글 수.</li>
 *   <li>👍: 로그인 필수, 남의 공개 댓글·답글에만, 한 사람 한 번(끄면 행 삭제). 🔥·관심과 합쳐 1분 20번(429).
 *       목록 order=popular 면 최상위 댓글을 👍 많은 순(같으면 최신 순)으로.</li>
 *   <li>삭제: 작성자 본인은 DELETED 로 바꾼다(행은 신고 검토·빈도 제한 근거로 남고 제보·탈퇴와 함께 DB 에서 지워진다).
 *       공개 답글이 남은 최상위 댓글은 목록에 "삭제된 댓글"(placeholder) 자리로 남고, 없으면 목록에서 빠진다.</li>
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
    /** 목록에서 최상위 댓글마다 바로 붙여 주는 답글 수. 나머지는 GET .../replies. */
    static final int INLINE_REPLIES = 3;
    /** "신고된 댓글" 목록 최대 개수(최근 신고 순). 더 많으면 처리하면서 줄어든 뒤 다시 본다. */
    static final int FLAGGED_LIST_LIMIT = 200;
    private static final Set<ReportCommentStatus> ADMIN_TARGETS = EnumSet.allOf(ReportCommentStatus.class);

    private final ReportCommentRepository commentRepository;
    private final ReportCommentFlagRepository flagRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final CommentAuthorKeys authorKeys;
    private final ContentFilter contentFilter;
    private final ApplicationEventPublisher eventPublisher;
    private final ReportCommentLikeRepository likeRepository;
    private final CommunityActionLimiter actionLimiter;
    private final CommentFlagLimiter flagLimiter;

    @Value("${report.comment.flag-threshold:3}")
    private long flagThreshold;

    @Value("${report.comment.rate-per-minute:5}")
    private long ratePerMinute;

    @Value("${report.comment.rate-per-day:50}")
    private long ratePerDay;

    // ---------- 공개 ----------

    /**
     * 최상위 댓글 한 페이지 + 각 댓글의 공개 답글 앞쪽 {@value #INLINE_REPLIES}개와 답글 수.
     * order: "oldest"(기본, 오래된 순) / "latest"(최신 순 — 시트 미리보기용) / "popular"(👍 많은 순, 같으면 최신 순).
     * 답글은 늘 오래된 순.
     * 쿼리: 최상위 1 + count 1 + 답글 묶음 1 + 전체 댓글 수 1 + 👍 수 1(+ 로그인이면 내 👍 1)(제보 확인 1 별도).
     */
    @Transactional(readOnly = true)
    public CommentListResponse list(Long requesterId, Long reportId, int page, int size, String order) {
        requireVisibleReport(reportId);
        Page<ReportComment> roots;
        if ("popular".equalsIgnoreCase(order)) {
            roots = commentRepository.findThreadRootsByLikes(reportId, PageRequest.of(Math.max(page, 0), pageSize(size)));
        } else {
            Sort sort = "latest".equalsIgnoreCase(order) ? Sort.by(Sort.Direction.DESC, "id") : Sort.by("id");
            roots = commentRepository.findThreadRoots(reportId, PageRequest.of(Math.max(page, 0), pageSize(size), sort));
        }

        Map<Long, List<ReportComment>> repliesByParent = new HashMap<>();
        if (!roots.isEmpty()) {
            List<Long> ids = roots.getContent().stream().map(ReportComment::getId).toList();
            for (ReportComment reply : commentRepository.findVisibleRepliesByParentIds(ids)) {
                repliesByParent.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>()).add(reply);
            }
        }
        List<CommentResponse> content = roots.getContent().stream().map(root -> {
            List<ReportComment> replies = repliesByParent.getOrDefault(root.getId(), List.of());
            List<CommentResponse> inline = replies.stream().limit(INLINE_REPLIES)
                    .map(reply -> toResponse(reply, requesterId)).toList();
            return CommentResponse.of(root, requesterId, authorKeys.of(root.getUser().getId()), inline, replies.size());
        }).toList();
        long commentCount = commentRepository.countByReport_IdAndStatus(reportId, ReportCommentStatus.VISIBLE);
        return CommentListResponse.of(roots, attachLikes(content, requesterId), commentCount);
    }

    /** 한 최상위 댓글의 공개 답글(오래된 순). "답글 N개 더 보기"용. */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> replies(Long requesterId, Long reportId, Long commentId, int page, int size) {
        requireVisibleReport(reportId);
        ReportComment parent = requireComment(reportId, commentId);
        if (parent.isReply()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "답글에는 답글 목록이 없어요.");
        }
        var result = commentRepository.findByParent_IdAndStatus(commentId, ReportCommentStatus.VISIBLE,
                PageRequest.of(Math.max(page, 0), pageSize(size), Sort.by("id")));
        PageResponse<CommentResponse> pageResponse = PageResponse.of(result.map(c -> toResponse(c, requesterId)));
        return new PageResponse<>(attachLikes(pageResponse.content(), requesterId), pageResponse.page(),
                pageResponse.size(), pageResponse.totalElements(), pageResponse.totalPages(), pageResponse.hasNext());
    }

    @Transactional
    public CommentResponse create(Long userId, Long reportId, String rawContent, Long parentId) {
        String content = rawContent == null ? "" : rawContent.strip();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해 주세요.");
        }
        if (content.length() > ReportComment.MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "댓글은 " + ReportComment.MAX_LENGTH + "자까지 쓸 수 있어요.");
        }
        // 내용은 로그에 남기지 않는다(개인정보) — 사유만.
        ContentViolation violation = contentFilter.check(content).orElse(null);
        if (violation != null) {
            log.info("댓글 필터 차단 reason={} reportId={}", violation, reportId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, violation.message());
        }
        Report report = requireVisibleReport(reportId);
        LocalDateTime now = LocalDateTime.now();
        if (report.getEndsAt() != null && report.getEndsAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "끝난 제보에는 댓글을 달 수 없어요.");
        }
        // 답글은 한 단계만: 답글에 답하면 그 답글의 최상위 댓글에 붙인다. 공개 중인 댓글에만 답할 수 있다.
        ReportComment parent = null;
        if (parentId != null) {
            ReportComment target = requireComment(reportId, parentId);
            if (target.getStatus() != ReportCommentStatus.VISIBLE) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "답글을 달 댓글이 없어요.");
            }
            parent = target.isReply() ? target.getParent() : target;
        }
        if (commentRepository.countByUser_IdAndCreatedAtAfter(userId, now.minusMinutes(1)) >= ratePerMinute
                || commentRepository.countByUser_IdAndCreatedAtAfter(userId, now.minusDays(1)) >= ratePerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "댓글을 너무 자주 달고 있어요. 잠시 뒤에 다시 시도해 주세요.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));

        ReportComment saved = commentRepository.save(new ReportComment(report, user, content, parent));
        // 제보 작성자("내 제보에 댓글")·부모 댓글 작성자("내 댓글에 답글")에게 커밋 뒤 비동기 푸시.
        // 본인·설정 끔·10분 묶음은 디스패처가 거른다.
        eventPublisher.publishEvent(new ReportCommentCreatedEvent(
                reportId, report.getUser().getId(), userId, report.getTitle(), content,
                parent == null ? null : parent.getId(), parent == null ? null : parent.getUser().getId()));
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
        // 받은 값을 그대로 돌려주지 않는다(응답·로그에 임의 문자열을 되비추지 않게).
        if (reason == null || !FLAG_REASONS.contains(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 신고 사유입니다.");
        }
        // 목록·쓰기와 같이 지도에 공개된 제보의 댓글만 — 비공개 제보는 댓글이 있는지도 드러내지 않게 404.
        Report report = requireVisibleReport(reportId);
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
        // 검증을 다 통과한 신고만 센다(잘못된 사유·중복 409 는 한도를 깎지 않는다).
        if (!flagLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "신고를 너무 자주 하고 있어요. 잠시 뒤에 다시 시도해 주세요.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));
        Long authorId = comment.getUser().getId();
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
            // 조건부 UPDATE 라 동시 신고에도 한 번만 들어온다. 관리자 알림(묶음)과 작성자 알림(사유·이의 제기 안내) 둘 다 커밋 뒤 비동기.
            log.info("댓글 신고 누적 자동 숨김: reportId={}, commentId={}, flags={}", reportId, commentId, sinceReview);
            eventPublisher.publishEvent(AdminAlertEvent.commentFlagged(reportId, commentId, userId, report.getTitle(), true));
            eventPublisher.publishEvent(new ReportCommentModeratedEvent(commentId, reportId, authorId, report.getTitle(),
                    ReportCommentStatus.HIDDEN, null, true));
        } else if (sinceReview == 1) {
            // 검토 뒤 첫 신고만 알린다 — 같은 댓글의 2번째 신고부터는 이미 "검토 필요"에 올라 있다.
            eventPublisher.publishEvent(AdminAlertEvent.commentFlagged(reportId, commentId, userId, report.getTitle(), false));
        }
        return new CommentFlagResponse(flagRepository.countByComment_Id(commentId), hidden);
    }

    /** 👍 누르기(on=true)·끄기. 남의 공개 댓글만(내 댓글 400, 공개 중이 아니면 404). */
    @Transactional
    public CommentLikeResponse setLike(Long userId, Long reportId, Long commentId, boolean on) {
        requireVisibleReport(reportId);
        ReportComment comment = requireComment(reportId, commentId);
        if (comment.getStatus() != ReportCommentStatus.VISIBLE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다.");
        }
        if (comment.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내 댓글에는 좋아요를 누를 수 없어요.");
        }
        if (!actionLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "너무 자주 누르고 있어요. 잠시 뒤에 다시 시도해 주세요.");
        }
        if (on) {
            if (!likeRepository.existsByComment_IdAndUser_Id(commentId, userId)) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자입니다."));
                try {
                    likeRepository.saveAndFlush(new ReportCommentLike(comment, user));
                } catch (DataIntegrityViolationException e) {
                    // 동시에 두 번 — 이미 눌린 상태로 본다.
                }
            }
        } else {
            likeRepository.deleteMine(commentId, userId);
        }
        return new CommentLikeResponse(on, likeRepository.countByComment_Id(commentId));
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

    /**
     * 신고된 댓글(GET /admin/comments?filter=flagged): 마지막 검토 뒤 신고가 있는 공개·숨김 댓글, 최근 신고 순 최대
     * {@value #FLAGGED_LIST_LIMIT}개. 쿼리: 목록 1(작성자·제보 함께) + 수 1 + 사유별 1 + 검토 뒤 신고 1.
     */
    @Transactional(readOnly = true)
    public AdminFlaggedCommentListResponse adminFlagged(String filter) {
        if (filter != null && !filter.isBlank() && !"flagged".equalsIgnoreCase(filter.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filter 는 flagged 만 쓸 수 있어요.");
        }
        List<ReportComment> comments = commentRepository.findFlaggedPending(PageRequest.of(0, FLAGGED_LIST_LIMIT));
        long total = comments.size() < FLAGGED_LIST_LIMIT ? comments.size() : commentRepository.countFlaggedPending();
        if (comments.isEmpty()) {
            return new AdminFlaggedCommentListResponse(List.of(), total);
        }
        Map<Long, Map<String, Long>> reasons = flagReasons(comments);
        Map<Long, Object[]> pending = new HashMap<>();
        for (Object[] row : flagRepository.countPendingByCommentIds(comments.stream().map(ReportComment::getId).toList())) {
            pending.put((Long) row[0], row);
        }
        return new AdminFlaggedCommentListResponse(comments.stream().map(c -> {
            Object[] row = pending.get(c.getId());
            return AdminFlaggedCommentResponse.of(c, reasons.get(c.getId()),
                    row == null ? 0 : (Long) row[1], row == null ? null : (LocalDateTime) row[2]);
        }).toList(), total);
    }

    /** 관리자 대시보드의 검토할 신고된 댓글 수(comments.flaggedPending). */
    @Transactional(readOnly = true)
    public long countFlaggedPending() {
        return commentRepository.countFlaggedPending();
    }

    /**
     * 관리자 상태 변경. 늘 reviewedAt 을 지금으로 남겨 그 전 신고는 자동 숨김·"신고된 댓글"에서 더 세지 않는다 —
     * 그래서 이미 공개 중인 댓글에 VISIBLE 을 주면 "검토 완료(유지)"가 된다.
     * 숨김·삭제로 바뀌면(또는 자동 숨김된 댓글을 숨김으로 확정하면) 작성자에게 사유와 이의 제기 안내를 알린다(커밋 뒤 비동기).
     * 작성자가 스스로 지운 댓글은 알리지 않는다.
     */
    @Transactional
    public AdminCommentResponse moderate(Long commentId, String status, String reason) {
        ReportCommentStatus target = parseTarget(status);
        ReportComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."));
        ReportCommentStatus previous = comment.getStatus();
        // 자동 숨김 뒤 아직 검토 전이면 작성자는 "운영진 확인 전까지 숨김"만 받았다 — 숨김 확정도 사유와 함께 다시 알린다.
        boolean autoHiddenPending = previous == ReportCommentStatus.HIDDEN && hasPendingFlags(comment);
        comment.moderate(target, LocalDateTime.now());
        boolean notify = target != ReportCommentStatus.VISIBLE && previous != ReportCommentStatus.DELETED
                && (target != previous || autoHiddenPending);
        if (notify) {
            String trimmed = reason == null || reason.isBlank() ? null : reason.strip();
            eventPublisher.publishEvent(new ReportCommentModeratedEvent(commentId, comment.getReport().getId(),
                    comment.getUser().getId(), comment.getReport().getTitle(), target, trimmed, false));
        }
        return AdminCommentResponse.of(comment, flagReasons(List.of(comment)).get(commentId));
    }

    // ---------- 내부 ----------

    private boolean hasPendingFlags(ReportComment comment) {
        return comment.getReviewedAt() == null
                ? flagRepository.countByComment_Id(comment.getId()) > 0
                : flagRepository.countByComment_IdAndCreatedAtAfter(comment.getId(), comment.getReviewedAt()) > 0;
    }

    /** 답글·새 댓글 응답(답글 목록 없이). */
    private CommentResponse toResponse(ReportComment comment, Long requesterId) {
        return CommentResponse.of(comment, requesterId, authorKeys.of(comment.getUser().getId()),
                comment.isReply() ? null : List.of(), 0);
    }

    /** 댓글(과 붙은 답글)에 👍 수·내 👍·내 신고를 붙인다. 쿼리: 수 1 + (로그인이면) 내 👍 1 + 내 신고 1. */
    private List<CommentResponse> attachLikes(List<CommentResponse> comments, Long requesterId) {
        if (comments.isEmpty()) {
            return comments;
        }
        List<Long> ids = new ArrayList<>();
        for (CommentResponse c : comments) {
            ids.add(c.id());
            if (c.replies() != null) {
                c.replies().forEach(r -> ids.add(r.id()));
            }
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : likeRepository.countByCommentIds(ids)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        Set<Long> mine = requesterId == null ? Set.of() : new HashSet<>(likeRepository.findLikedCommentIds(ids, requesterId));
        Set<Long> flagged = requesterId == null ? Set.of()
                : new HashSet<>(flagRepository.findFlaggedCommentIds(ids, requesterId));
        return comments.stream().map(c -> c.withViewerState(counts.getOrDefault(c.id(), 0L), mine.contains(c.id()),
                flagged.contains(c.id()),
                c.replies() == null ? null : c.replies().stream()
                        .map(r -> r.withViewerState(counts.getOrDefault(r.id(), 0L), mine.contains(r.id()),
                                flagged.contains(r.id()), r.replies()))
                        .toList())).toList();
    }

    private static int pageSize(int size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
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
