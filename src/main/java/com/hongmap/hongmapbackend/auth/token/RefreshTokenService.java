package com.hongmap.hongmapbackend.auth.token;

import com.hongmap.hongmapbackend.auth.dto.TokenResponse;
import com.hongmap.hongmapbackend.auth.jwt.JwtProperties;
import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * refresh 토큰의 서버 측 보관/검증/무효화를 담당. JWT 자체의 서명·만료 검증은
 * JwtTokenProvider에 위임하고, 여기서는 "이 토큰이 아직 유효한 세션인지"만 DB로 추가 확인한다.
 * 유저당 활성 refresh 토큰은 1개(재로그인/재발급 시 로테이션되어 이전 토큰은 즉시 무효).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenResponse issueTokenPair(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
        saveOrRotate(user, refreshToken);

        return new TokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        Long userId = requireValidRefreshToken(refreshToken);

        RefreshToken stored = refreshTokenRepository.findByUser_Id(userId)
                .filter(entry -> entry.getTokenHash().equals(hash(refreshToken)))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "로그아웃되었거나 폐기된 refresh 토큰입니다."));

        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        stored.rotate(hash(newRefreshToken), expirationOf());

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void revoke(String refreshToken) {
        if (refreshToken == null
                || !jwtTokenProvider.validateToken(refreshToken)
                || jwtTokenProvider.isAccessToken(refreshToken)) {
            return; // 이미 만료/무효한 토큰 — 로그아웃은 멱등하게 조용히 종료
        }

        refreshTokenRepository.deleteByUser_Id(jwtTokenProvider.getUserId(refreshToken));
    }

    private Long requireValidRefreshToken(String refreshToken) {
        if (refreshToken == null
                || !jwtTokenProvider.validateToken(refreshToken)
                || jwtTokenProvider.isAccessToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 refresh 토큰입니다.");
        }
        return jwtTokenProvider.getUserId(refreshToken);
    }

    private void saveOrRotate(User user, String refreshToken) {
        String tokenHash = hash(refreshToken);
        LocalDateTime expiresAt = expirationOf();

        refreshTokenRepository.findByUser_Id(user.getId())
                .ifPresentOrElse(
                        entry -> entry.rotate(tokenHash, expiresAt),
                        () -> refreshTokenRepository.save(new RefreshToken(user, tokenHash, expiresAt))
                );
    }

    private LocalDateTime expirationOf() {
        return LocalDateTime.now().plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));
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
