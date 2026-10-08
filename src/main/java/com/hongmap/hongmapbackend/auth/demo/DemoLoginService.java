package com.hongmap.hongmapbackend.auth.demo;

import com.hongmap.hongmapbackend.auth.dto.TokenResponse;
import com.hongmap.hongmapbackend.auth.token.RefreshTokenService;
import com.hongmap.hongmapbackend.common.ratelimit.SlidingWindowRateLimiter;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.hongmap.hongmapbackend.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;

/**
 * 앱 심사(App Store / TestFlight Beta App Review)용 데모 로그인. 앱은 카카오·Apple 로그인만 있어 심사관이 쓸
 * 아이디/비밀번호 계정이 따로 필요하다. 서버 환경 변수로만 켜고(기본 꺼짐), 심사가 끝나면 끈다.
 *
 * <ul>
 *   <li>꺼져 있거나 설정이 불완전하면(아이디·비밀번호가 비었거나 비밀번호가 12자 미만) 404 — 엔드포인트가 없는 것처럼 보인다.
 *       불완전한 설정은 기동 때 WARN 한 번.</li>
 *   <li>접속 IP 마다 10분에 10번까지(성공·실패 모두 셈, 넘으면 429). IP 는 FeedbackRateLimiter 와 같은
 *       request.getRemoteAddr()(server.forward-headers-strategy=framework, nginx 가 덮어쓴 X-Forwarded-For).</li>
 *   <li>아이디·비밀번호는 SHA-256 다이제스트를 MessageDigest.isEqual 로 비교(길이·내용에 따른 시간 차 없음), 둘 다 항상 비교한다.
 *       틀리면 어느 쪽이 틀렸는지 알리지 않는 401.</li>
 *   <li>로그인하면 DEMO 소셜 타입의 고정 회원 하나를 찾거나 만들고, 카카오·Apple 과 같은 RefreshTokenService.issueTokenPair 로 토큰을 준다.
 *       그 회원이 ADMIN 이면 거절(401) + ERROR 로그 — 데모 계정으로 관리자 화면에 들어갈 수 없게.</li>
 *   <li>입력한 아이디·비밀번호는 로그에 남기지 않는다(결과와 가린 IP 만).</li>
 * </ul>
 */
@Slf4j
@Service
public class DemoLoginService {

    /** 데모 회원의 social_id. 환경 변수 아이디를 바꿔도 같은 회원을 쓴다. */
    static final String DEMO_SOCIAL_ID = "app-review-demo";
    static final String DEMO_NICKNAME = "앱 심사 계정";
    static final int MIN_PASSWORD_LENGTH = 12;
    static final String INVALID_CREDENTIALS_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";
    static final String TOO_MANY_MESSAGE = "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.";

    private final boolean active;
    private final byte[] usernameDigest;
    private final byte[] passwordDigest;
    private final SlidingWindowRateLimiter limiter;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Autowired
    public DemoLoginService(@Value("${app.demo-login.enabled:false}") boolean enabled,
                            @Value("${app.demo-login.username:}") String username,
                            @Value("${app.demo-login.password:}") String password,
                            @Value("${app.demo-login.rate-limit.max-attempts:10}") int maxAttempts,
                            @Value("${app.demo-login.rate-limit.window-minutes:10}") long windowMinutes,
                            UserRepository userRepository,
                            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.limiter = new SlidingWindowRateLimiter(maxAttempts, Duration.ofMinutes(windowMinutes), Clock.systemUTC());

        boolean configured = username != null && !username.isBlank()
                && password != null && password.length() >= MIN_PASSWORD_LENGTH;
        this.active = enabled && configured;
        this.usernameDigest = active ? sha256(username) : null;
        this.passwordDigest = active ? sha256(password) : null;

        if (enabled && !configured) {
            log.warn("demo-login: DEMO_LOGIN_ENABLED=true 이지만 DEMO_LOGIN_USERNAME 이 비었거나 "
                    + "DEMO_LOGIN_PASSWORD 가 {}자 미만이라 끈 상태로 둡니다(POST /auth/demo → 404).", MIN_PASSWORD_LENGTH);
        } else if (active) {
            log.warn("demo-login: 앱 심사용 데모 로그인이 켜져 있습니다(POST /auth/demo). 심사가 끝나면 DEMO_LOGIN_ENABLED=false 로 끄세요.");
        }
    }

    public boolean isActive() {
        return active;
    }

    /** 꺼져 있으면 404. 켜져 있을 때만 시도 횟수를 센다(꺼진 상태와 구별되지 않게). */
    public void requireActive() {
        if (!active) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** 시도 1회를 기록하고, 한도를 넘었으면 429. */
    public void acquireAttempt(String clientIp) {
        String key = "ip:" + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
        if (!limiter.tryAcquire(key)) {
            log.warn("demo-login result=ratelimited ip={}", maskIp(clientIp));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_MESSAGE);
        }
    }

    public TokenResponse login(String username, String password, String clientIp) {
        requireActive();
        acquireAttempt(clientIp);

        // 비단락 & — 아이디가 틀려도 비밀번호 비교를 건너뛰지 않는다.
        boolean usernameOk = MessageDigest.isEqual(usernameDigest, sha256(username == null ? "" : username));
        boolean passwordOk = MessageDigest.isEqual(passwordDigest, sha256(password == null ? "" : password));
        if (!(usernameOk & passwordOk)) {
            log.info("demo-login result=fail ip={}", maskIp(clientIp));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        User user = findOrCreateDemoUser();
        if (user.getRole() != UserRole.USER) {
            log.error("demo-login: 데모 회원(userId={})이 {} 권한이라 로그인을 거절합니다. users.role 을 USER 로 되돌리세요.",
                    user.getId(), user.getRole());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        TokenResponse tokens = refreshTokenService.issueTokenPair(user.getId());
        log.info("demo-login result=success userId={} ip={}", user.getId(), maskIp(clientIp));
        return tokens;
    }

    private User findOrCreateDemoUser() {
        return userRepository.findBySocialTypeAndSocialId(SocialType.DEMO, DEMO_SOCIAL_ID)
                .orElseGet(() -> {
                    try {
                        // role 은 엔티티 기본값 USER. 이메일 등 개인정보는 없다.
                        return userRepository.save(User.builder()
                                .socialType(SocialType.DEMO)
                                .socialId(DEMO_SOCIAL_ID)
                                .nickname(DEMO_NICKNAME)
                                .build());
                    } catch (DataIntegrityViolationException e) {
                        // 첫 로그인이 동시에 두 번 들어온 경우 — 먼저 저장된 행을 쓴다.
                        return userRepository.findBySocialTypeAndSocialId(SocialType.DEMO, DEMO_SOCIAL_ID)
                                .orElseThrow(() -> e);
                    }
                });
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 로그용으로 IP 끝부분을 가린다(IPv4 마지막 옥텟, IPv6 앞 3그룹만). */
    static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        if (ip.contains(":")) {
            String[] groups = ip.split(":", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, groups.length); i++) {
                sb.append(groups[i]).append(':');
            }
            return sb.append('*').toString();
        }
        int lastDot = ip.lastIndexOf('.');
        return lastDot < 0 ? "*" : ip.substring(0, lastDot) + ".*";
    }
}
