package com.hongmap.hongmapbackend.feedback;

import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import com.hongmap.hongmapbackend.feedback.dto.FeedbackCreateRequest;
import com.hongmap.hongmapbackend.feedback.dto.FeedbackListResponse;
import com.hongmap.hongmapbackend.feedback.dto.FeedbackResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import com.hongmap.hongmapbackend.report.image.ReportImageService;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final int LIST_LIMIT = 200;

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReportImageService reportImageService;

    /** userId 가 null 이면(게스트) 작성자 없이 저장한다. 탈퇴 직후의 오래된 토큰이어도 문의는 받는다. */
    @Transactional
    public void create(Long userId, FeedbackCreateRequest request) {
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        String contact = request.contact() == null || request.contact().isBlank() ? null : request.contact().trim();
        List<String> imageKeys = List.of();
        if (request.imageKeys() != null && !request.imageKeys().isEmpty()) {
            // 사진은 로그인한 문의만(누가 올렸는지 알 수 있어야 하고, 업로드 URL 발급도 로그인 전용이다).
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사진을 붙이려면 로그인해 주세요.");
            }
            // 제보 사진과 같은 검사: 서버 발급 키·실제 업로드·크기·형식 확인, 메타데이터(GPS 등) 제거 후 새 키로 저장.
            imageKeys = reportImageService.validateForAttach(request.imageKeys());
        }
        Feedback saved = feedbackRepository.save(new Feedback(user, request.content().trim(), contact, imageKeys));
        // 관리자 "새 문의" 알림 — 내용·연락처는 푸시에 싣지 않는다(AdminAlertDispatcher).
        eventPublisher.publishEvent(AdminAlertEvent.feedback(saved.getId(), user == null ? null : user.getId()));
    }

    @Transactional(readOnly = true)
    public FeedbackListResponse list(String status) {
        FeedbackStatus filter;
        if (status == null || status.isBlank()) {
            filter = FeedbackStatus.OPEN;
        } else if ("ALL".equalsIgnoreCase(status)) {
            filter = null;
        } else {
            filter = parse(status);
        }
        return new FeedbackListResponse(feedbackRepository.findForAdmin(filter, PageRequest.of(0, LIST_LIMIT)).stream()
                .map(this::toResponse)
                .toList());
    }

    @Transactional
    public FeedbackResponse changeStatus(Long id, String status) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 문의입니다."));
        FeedbackStatus next = parse(status);
        feedback.changeStatus(next, LocalDateTime.now());
        // 처리가 끝나면 참고 사진은 남기지 않는다(확인용으로만 받았다 — 최소 보관).
        if (next == FeedbackStatus.RESOLVED && !feedback.getImageKeyList().isEmpty()) {
            reportImageService.deleteAfterCommit(feedback.getImageKeyList());
            feedback.clearImages();
        }
        return toResponse(feedback);
    }

    private FeedbackResponse toResponse(Feedback feedback) {
        return FeedbackResponse.of(feedback, reportImageService.viewUrls(feedback.getImageKeyList()));
    }

    private FeedbackStatus parse(String status) {
        try {
            return FeedbackStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 상태입니다: " + status);
        }
    }
}
