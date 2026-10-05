package com.hongmap.hongmapbackend.auth.token;

import com.hongmap.hongmapbackend.auth.dto.TokenResponse;
import com.hongmap.hongmapbackend.auth.jwt.JwtProperties;
import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * refresh 토큰의 서버 측 보관/검증/무효화를 담당. JWT 자체의 서명·만료 검증은
 * JwtTokenProvider에 위임하고, 여기서는 "이 토큰이 아직 유효한 세션인지"만 DB로 추가 확인한다.
 *
 * <p>세션 = 로그인 1번으로 시작되는 토큰 사슬 = refresh_tokens row 1개. 유저당 여러 세션(앱·웹·관리자 콘솔·여러 기기)이
 * 동시에 살아 있고, 상한(jwt.max-sessions-per-user, 기본 10)을 넘으면 가장 오래 안 쓴 세션부터 지운다.
 * 예전엔 유저당 1 row 라 ① 다른 기기 로그인 ② 같은 토큰 동시 재발급(웹 여러 탭) ③ 재발급 응답 유실 재시도에서
 * 다른 쪽이 401 → "로그인이 만료됐어요"로 튕겼다.
 *
 * <p>재발급 유예: reissue 는 row 를 잠그고(SELECT ... FOR UPDATE) 현재 해시면 로테이션하며 직전 해시를 previous 에 남긴다.
 * 직전 토큰이 유예 시간(jwt.refresh-reuse-grace-seconds, 기본 60초) 안에 다시 오면 — 같은 토큰을 동시에 보낸 다른 탭,
 * 또는 응답을 못 받고 재시도한 앱 — 그 row 는 그대로 두고 <b>새 세션 row 를 하나 더</b> 만들어 준다(fork).
 * 같은 row 를 다시 로테이션하면 먼저 받은 쪽 토큰이 previous 로 밀려 유예가 끝나는 순간(보통 다음 재발급인 30분 뒤) 401 이
 * 되므로, 두 쪽 모두 자기 토큰으로 계속 재발급할 수 있게 사슬을 갈라 준다. 쓰이지 않는 쪽 사슬은 만료(14일)·정기 정리·
 * 세션 상한으로 사라진다. 유예가 지난 직전 토큰 재사용은 401(세션은 지우지 않음 — 아래 reissue 주석).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REVOKED_MESSAGE = "로그아웃되었거나 폐기된 refresh 토큰입니다.";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    /** 로그인: 기존 세션은 건드리지 않고 새 세션을 하나 만든다(만료 세션 정리 + 상한 초과분 삭제). */
    @Transactional
    public TokenResponse issueTokenPair(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        return startSession(user, LocalDateTime.now());
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        Long userId = requireValidRefreshToken(refreshToken);
        String tokenHash = hash(refreshToken);
        LocalDateTime now = LocalDateTime.now();

        // 1) 현재 토큰 — 정상 로테이션. 배포 전부터 있던 row 도 token_hash 가 현재 토큰이라 그대로 여기로 온다.
        Optional<RefreshToken> current = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .filter(entry -> userId.equals(entry.getUser().getId()));
        if (current.isPresent()) {
            String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
            current.get().rotate(hash(newRefreshToken), expirationOf(now), now);
            return new TokenResponse(newAccessToken, newRefreshToken);
        }

        // 2) 방금 로테이션된 직전 토큰 — 동시 재발급/응답 유실 재시도. 유예 안이면 같은 유저의 새 세션으로 갈라 준다.
        //    먼저 로테이션한 쪽(현재 토큰 보유)은 영향 없음.
        RefreshToken previous = refreshTokenRepository.findByPreviousTokenHashForUpdate(tokenHash).stream()
                .filter(entry -> userId.equals(entry.getUser().getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, REVOKED_MESSAGE));
        if (previous.isWithinReuseGrace(now, jwtProperties.getRefreshReuseGraceSeconds())) {
            log.info("refresh 직전 토큰 유예 재사용 → 세션 분기 userId={} sessionId={}", userId, previous.getId());
            return startSession(previous.getUser(), now);
        }

        // 3) 유예가 지난 직전 토큰 — 401. 탈취 의심으로 세션(row)까지 지우는 방식도 있지만, 그러면 늦게 재시도한 정상 기기
        //    (응답 유실 후 한참 뒤 재시도, 오래 열어 둔 탭) 하나 때문에 그 세션을 쓰는 다른 쪽까지 로그아웃돼 이번 수정의 목적과
        //    어긋난다. 토큰은 해시만 저장·HTTPS 전송이고 로테이션으로 탈취 토큰도 유예 뒤엔 못 쓰므로 401 + 경고 로그로 둔다.
        log.warn("유예가 지난 직전 refresh 토큰 재사용 userId={} sessionId={}", userId, previous.getId());
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, REVOKED_MESSAGE);
    }

    /** 로그아웃: 이 토큰의 세션 row 만 지운다(같은 유저의 다른 기기·브라우저 세션은 유지). 멱등. */
    @Transactional
    public void revoke(String refreshToken) {
        if (refreshToken == null
                || !jwtTokenProvider.validateToken(refreshToken)
                || jwtTokenProvider.isAccessToken(refreshToken)) {
            return; // 이미 만료/무효한 토큰 — 로그아웃은 멱등하게 조용히 종료
        }
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String tokenHash = hash(refreshToken);

        // 현재 토큰이면 그 세션, 아니면 직전 토큰(동시 재발급으로 한 박자 늦은 탭)의 세션을 지운다.
        Optional<RefreshToken> session = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .or(() -> refreshTokenRepository.findByPreviousTokenHashForUpdate(tokenHash).stream().findFirst())
                .filter(entry -> userId.equals(entry.getUser().getId()));
        session.ifPresent(refreshTokenRepository::delete);
    }

    private TokenResponse startSession(User user, LocalDateTime now) {
        pruneSessions(user.getId(), now);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        refreshTokenRepository.save(new RefreshToken(user, hash(refreshToken), expirationOf(now)));

        return new TokenResponse(accessToken, refreshToken);
    }

    /** 새 세션을 넣기 전에 만료된 세션과, 새 세션까지 합쳐 상한을 넘게 되는 가장 오래 안 쓴 세션을 지운다. */
    private void pruneSessions(Long userId, LocalDateTime now) {
        int keep = jwtProperties.getMaxSessionsPerUser() - 1;
        List<RefreshToken> stale = new ArrayList<>();
        int alive = 0;
        for (RefreshToken session : refreshTokenRepository.findByUser_IdOrderByUpdatedAtDescIdDesc(userId)) {
            if (session.getExpiresAt().isBefore(now) || alive >= keep) {
                stale.add(session);
            } else {
                alive++;
            }
        }
        if (!stale.isEmpty()) {
            refreshTokenRepository.deleteAll(stale);
        }
    }

    private Long requireValidRefreshToken(String refreshToken) {
        if (refreshToken == null
                || !jwtTokenProvider.validateToken(refreshToken)
                || jwtTokenProvider.isAccessToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 refresh 토큰입니다.");
        }
        return jwtTokenProvider.getUserId(refreshToken);
    }

    private LocalDateTime expirationOf(LocalDateTime now) {
        return now.plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
