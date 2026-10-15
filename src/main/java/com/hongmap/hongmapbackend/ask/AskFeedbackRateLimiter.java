package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.common.ratelimit.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;

/**
 * POST /ask/feedback 도배 방지. 댓글 신고 제한(CommentFlagLimiter)과 같은 방식 — 서버 메모리의
 * SlidingWindowRateLimiter 로 기본 10분에 10번, 넘으면 429.
 * 키는 로그인했으면 사용자(user:id), 아니면 접속 IP(ip:주소). IP 는 FeedbackRateLimiter 와 같은 request.getRemoteAddr().
 * 서버 1대 메모리 기준이라 재시작하면 초기화된다. 로그 자체에는 사용자·IP 를 남기지 않는다.
 */
@Component
public class AskFeedbackRateLimiter {

    static final String TOO_MANY_MESSAGE = "요청이 너무 잦아요. 잠시 후 다시 시도해 주세요.";

    private final SlidingWindowRateLimiter limiter;

    @Autowired
    public AskFeedbackRateLimiter(@Value("${ask.feedback.rate-limit.max-requests:10}") int maxRequests,
                                  @Value("${ask.feedback.rate-limit.window-minutes:10}") long windowMinutes) {
        this(maxRequests, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
    }

    AskFeedbackRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.limiter = new SlidingWindowRateLimiter(maxRequests, window, clock);
    }

    /** 한도 안이면 기록하고 통과, 넘었으면 429. userId 는 게스트면 null. */
    public void acquire(String clientIp, Long userId) {
        String key = userId != null
                ? "user:" + userId
                : "ip:" + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
        if (!limiter.tryAcquire(key)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_MESSAGE);
        }
    }
}
