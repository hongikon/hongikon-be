package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.common.ratelimit.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * 댓글 신고 빈도 제한: 사용자마다 10분에 report.comment.flag-rate-per-10-minutes(기본 10)번.
 * 신고 3건이면 댓글이 자동으로 숨겨지므로, 한 사람이 여러 댓글을 몰아서 신고해 대화를 지우는 것(신고 남용)을 늦춘다.
 * 서버 메모리 기준(SlidingWindowRateLimiter — 재시작하면 초기화)이라 엄격한 제한은 아니다. 여러 계정을 쓰는 남용은
 * 관리자 알림(ADMIN_COMMENT_FLAGGED)과 "신고된 댓글" 목록에서 사람이 본다.
 */
@Component
public class CommentFlagLimiter {

    static final Duration WINDOW = Duration.ofMinutes(10);

    private final int limit;
    private SlidingWindowRateLimiter limiter;

    public CommentFlagLimiter(@Value("${report.comment.flag-rate-per-10-minutes:10}") int limit) {
        this.limit = limit;
        this.limiter = new SlidingWindowRateLimiter(limit, WINDOW, Clock.systemUTC());
    }

    /** 신고할 수 있으면 기록하고 true. 한도를 넘었으면 false. */
    public boolean tryAcquire(Long userId) {
        return limiter.tryAcquire("comment-flag:" + userId);
    }

    /** 테스트용 — 기록을 비운다. */
    void resetForTest() {
        this.limiter = new SlidingWindowRateLimiter(limit, WINDOW, Clock.systemUTC());
    }
}
