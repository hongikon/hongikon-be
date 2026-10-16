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
 * POST /ask/route 남용 방지. 요청마다 유료 LLM 을 부르므로 접속 IP 마다 기본 60분에 30건.
 * 넘으면 429 — 앱은 낱말 찾기로 돌아가니 사용자는 막히지 않는다. 서버 1대 메모리 기준(재시작하면 초기화).
 * IP 는 request.getRemoteAddr()(nginx 가 덮어쓴 X-Forwarded-For, FeedbackRateLimiter 와 같은 값).
 */
@Component
public class AskRateLimiter {

    static final String TOO_MANY_MESSAGE = "질문을 너무 자주 보내고 있어요. 잠시 후 다시 물어봐 주세요.";

    private final SlidingWindowRateLimiter limiter;

    @Autowired
    public AskRateLimiter(@Value("${ask.rate-limit.max-requests:30}") int maxRequests,
                          @Value("${ask.rate-limit.window-minutes:60}") long windowMinutes) {
        this(maxRequests, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
    }

    AskRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.limiter = new SlidingWindowRateLimiter(maxRequests, window, clock);
    }

    public void acquire(String clientIp) {
        String ipKey = "ip:" + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
        if (!limiter.tryAcquire(ipKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_MESSAGE);
        }
    }
}
