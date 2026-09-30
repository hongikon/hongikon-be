package com.hongmap.hongmapbackend.feedback;

import com.hongmap.hongmapbackend.feedback.dto.FeedbackCreateRequest;
import com.hongmap.hongmapbackend.feedback.dto.FeedbackListResponse;
import com.hongmap.hongmapbackend.feedback.dto.FeedbackResponse;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final int LIST_LIMIT = 200;

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    /** userId 가 null 이면(게스트) 작성자 없이 저장한다. 탈퇴 직후의 오래된 토큰이어도 문의는 받는다. */
    @Transactional
    public void create(Long userId, FeedbackCreateRequest request) {
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        String contact = request.contact() == null || request.contact().isBlank() ? null : request.contact().trim();
        feedbackRepository.save(new Feedback(user, request.content().trim(), contact));
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
                .map(FeedbackResponse::of)
                .toList());
    }

    @Transactional
    public FeedbackResponse changeStatus(Long id, String status) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 문의입니다."));
        feedback.changeStatus(parse(status), LocalDateTime.now());
        return FeedbackResponse.of(feedback);
    }

    private FeedbackStatus parse(String status) {
        try {
            return FeedbackStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 상태입니다: " + status);
        }
    }
}
