package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.comment.dto.AdminCommentListResponse;
import com.hongmap.hongmapbackend.comment.dto.AdminCommentResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentFlagResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentLikeResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentListResponse;
import com.hongmap.hongmapbackend.comment.dto.CommentResponse;
import com.hongmap.hongmapbackend.common.dto.PageResponse;
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
 *       정지 회원은 #13 의 SuspendedUserInterceptor 가 403 으로 막는다(ReportCommentWebConfig).</li>
 *   <li>사후 검토: 바로 공개, 신고가 report.comment.flag-threshold(기본 3)개 쌓이면 자동 숨김.
 *       관리자 검토(reviewedAt) 뒤의 신고만 센다 — 복원한 댓글을 옛 신고로 다시 숨기지 않는다.</li>
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
    private static final Set<ReportCommentStatus> ADMIN_TARGETS = EnumSet.allOf(ReportCommentStatus.class);

    private final ReportCommentRepository commentRepository;
    private final ReportCommentFlagRepository flagRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final CommentAuthorKeys authorKeys;
    private final ApplicationEventPublisher eventPublisher;
    private final ReportCommentLikeRepository likeRepository;
    private final CommunityActionLimiter actionLimiter;

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

    @Transactional
    public AdminCommentResponse moderate(Long commentId, String status) {
        ReportCommentStatus target = parseTarget(status);
        ReportComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."));
        comment.moderate(target, LocalDateTime.now());
        return AdminCommentResponse.of(comment, flagReasons(List.of(comment)).get(commentId));
    }

    // ---------- 내부 ----------

    /** 답글·새 댓글 응답(답글 목록 없이). */
    private CommentResponse toResponse(ReportComment comment, Long requesterId) {
        return CommentResponse.of(comment, requesterId, authorKeys.of(comment.getUser().getId()),
                comment.isReply() ? null : List.of(), 0);
    }

    /** 댓글(과 붙은 답글)에 👍 수·내 👍를 붙인다. 쿼리: 수 1 + (로그인이면) 내 👍 1. */
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
        return comments.stream().map(c -> c.withLikes(counts.getOrDefault(c.id(), 0L), mine.contains(c.id()),
                c.replies() == null ? null : c.replies().stream()
                        .map(r -> r.withLikes(counts.getOrDefault(r.id(), 0L), mine.contains(r.id()), r.replies()))
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
