package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.common.ratelimit.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * 앱 '물어보기' 탭(BETA, 11-01) — 로그인한 사용자의 질문 한도. docs: 프론트 docs/ask-tab-plan.md.
 *
 * - ask.public.enabled 가 켜져 있어야 로그인 사용자가 개발용 암호키 없이 물을 수 있다(기본 꺼짐).
 * - 하루(한국 날짜) 계정마다 user-daily-limit 번, 전체 daily-limit 번(예산 보호 천장). 짧은 시간 몰아치기는 burst 로 막는다.
 * - 답을 못 만든 질문(서버·LLM 실패)은 {@link #refund} 로 되돌려 한도를 깎지 않는다.
 * - 계정 번호는 이 메모리 안에서 세는 데만 쓰고, 대화 기록(ask_conversation_logs)에는 남기지 않는다.
 * 서버 1대 메모리 기준이라 재시작하면 그날 센 값이 0 이 된다(배포는 드물어 예산 오차가 작다).
 */
@Component
public class AskPublicQuota {

    static final String LOGIN_REQUIRED_MESSAGE = "로그인하면 물어볼 수 있어요.";
    static final String USER_LIMIT_MESSAGE = "오늘 질문을 모두 썼어요. 내일 다시 물어봐 주세요.";
    static final String DAILY_LIMIT_MESSAGE = "오늘 준비한 질문이 모두 찼어요(BETA). 내일 다시 물어봐 주세요.";
    static final String BURST_MESSAGE = "질문을 너무 빠르게 보내고 있어요. 잠시 후 다시 물어봐 주세요.";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final boolean enabled;
    private final int userDailyLimit;
    private final int dailyLimit;
    private final Clock clock;
    private final SlidingWindowRateLimiter burst;

    private LocalDate day;
    private int used;
    private final Map<Long, Integer> usedByUser = new HashMap<>();

    @Autowired
    public AskPublicQuota(@Value("${ask.public.enabled:false}") boolean enabled,
                          @Value("${ask.public.user-daily-limit:100}") int userDailyLimit,
                          @Value("${ask.public.daily-limit:150}") int dailyLimit,
                          @Value("${ask.public.burst-max:20}") int burstMax,
                          @Value("${ask.public.burst-minutes:10}") long burstMinutes) {
        this(enabled, userDailyLimit, dailyLimit, burstMax, Duration.ofMinutes(burstMinutes), Clock.systemUTC());
    }

    AskPublicQuota(boolean enabled, int userDailyLimit, int dailyLimit, int burstMax, Duration burstWindow, Clock clock) {
        if (userDailyLimit < 1 || dailyLimit < 1) {
            throw new IllegalArgumentException("ask.public 한도는 1 이상이어야 한다.");
        }
        this.enabled = enabled;
        this.userDailyLimit = userDailyLimit;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
        this.burst = new SlidingWindowRateLimiter(burstMax, burstWindow, clock);
    }

    public boolean enabled() {
        return enabled;
    }

    /** 이번 질문 한 번을 센다. 꽉 찼으면 429(세지 않는다). */
    public synchronized void acquire(long userId) {
        rollDay();
        int mine = usedByUser.getOrDefault(userId, 0);
        if (mine >= userDailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, USER_LIMIT_MESSAGE);
        }
        if (used >= dailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, DAILY_LIMIT_MESSAGE);
        }
        if (!burst.tryAcquire("user:" + userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, BURST_MESSAGE);
        }
        usedByUser.put(userId, mine + 1);
        used++;
    }

    /** 답을 못 만든 질문은 되돌린다(같은 날일 때만). */
    public synchronized void refund(long userId) {
        rollDay();
        Integer mine = usedByUser.get(userId);
        if (mine == null || mine == 0) {
            return;
        }
        usedByUser.put(userId, mine - 1);
        used = Math.max(0, used - 1);
    }

    /** 앱 화면용 남은 횟수. userId 가 없으면(게스트) 계정 한도 대신 0. */
    public Status status(Long userId) {
        return status(userId, enabled);
    }

    /** open: 이 사용자에게 탭이 열려 있는지(스위치가 꺼져도 운영진 미리보기면 true). */
    public synchronized Status status(Long userId, boolean open) {
        rollDay();
        int dailyLeft = Math.max(0, dailyLimit - used);
        int userLeft = userId == null ? 0 : Math.max(0, userDailyLimit - usedByUser.getOrDefault(userId, 0));
        return new Status(open, userId != null, userDailyLimit, Math.min(userLeft, dailyLeft), dailyLeft == 0);
    }

    private void rollDay() {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        if (!today.equals(day)) {
            day = today;
            used = 0;
            usedByUser.clear();
        }
    }

    /**
     * @param remaining 오늘 이 계정이 더 물을 수 있는 수(전체 남은 수보다 크지 않게)
     * @param soldOut   전체 하루 상한이 찼는지
     */
    public record Status(boolean enabled, boolean loggedIn, int limit, int remaining, boolean soldOut) {
    }
}
