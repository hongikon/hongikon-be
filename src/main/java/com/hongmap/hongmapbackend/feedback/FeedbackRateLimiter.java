package com.hongmap.hongmapbackend.feedback;

import com.hongmap.hongmapbackend.common.ratelimit.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;

/**
 * POST /feedback 도배 방지. 게스트도 보낼 수 있고 접수될 때마다 관리자 푸시(AdminAlertDispatcher)가 나가서,
 * 스크립트로 몇 백 건씩 넣으면 문의함·관리자 기기가 같이 마비된다.
 * 접속 IP 마다, 로그인했으면 사용자마다도 같은 한도(기본 10분에 5건)를 둔다 — 둘 중 하나라도 차면 429.
 *
 * IP 는 request.getRemoteAddr() — server.forward-headers-strategy=framework 라 nginx 가 덮어쓴 X-Forwarded-For
 * (클라이언트가 보낸 값은 버림, deploy/nginx/hongikon-api.conf)가 들어온다. AdminAuditInterceptor 와 같은 값.
 * 캠퍼스 와이파이처럼 여러 명이 IP 하나를 쓰면 함께 세지만, 문의가 10분에 5건 넘게 몰릴 일은 드물다.
 * 서버 1대 메모리 기준(재시작하면 초기화).
 */
@Component
public class FeedbackRateLimiter {

    static final String TOO_MANY_MESSAGE = "문의를 너무 자주 보내고 있어요. 잠시 후 다시 보내 주세요.";

    private final SlidingWindowRateLimiter limiter;

    @Autowired
    public FeedbackRateLimiter(@Value("${feedback.rate-limit.max-requests:5}") int maxRequests,
                               @Value("${feedback.rate-limit.window-minutes:10}") long windowMinutes) {
        this(maxRequests, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
    }

    FeedbackRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.limiter = new SlidingWindowRateLimiter(maxRequests, window, clock);
    }

    /** 한도 안이면 기록하고 통과, 넘었으면 429. userId 는 게스트면 null. */
    public void acquire(String clientIp, Long userId) {
        String ipKey = "ip:" + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
        boolean ok = userId == null ? limiter.tryAcquire(ipKey) : limiter.tryAcquireAll(ipKey, "user:" + userId);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_MESSAGE);
        }
    }
}
