package com.hongmap.hongmapbackend.auth.apple;

import com.hongmap.hongmapbackend.auth.dto.AppleLoginRequest;
import com.hongmap.hongmapbackend.auth.dto.TokenResponse;
import com.hongmap.hongmapbackend.auth.token.RefreshTokenService;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.hongmap.hongmapbackend.user.retention.WithdrawRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 앱(iOS)의 Sign in with Apple 결과로 로그인한다. 카카오와 달리 서버 리다이렉트 없이 앱이 받은 identity token을
 * 바로 검증하고, 카카오 토큰 교환(/auth/token/exchange)과 같은 access/refresh 토큰 쌍을 돌려준다.
 *
 * <p>Apple 서버 호출(authorization code 교환)은 DB 트랜잭션 밖에서 먼저 하고, 사용자 저장은 저장소 호출 단위로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppleLoginService {

    static final String DEFAULT_NICKNAME_PREFIX = "Apple 사용자";
    private static final int NICKNAME_MAX_LENGTH = 50;

    private final AppleIdentityTokenVerifier verifier;
    private final AppleAuthClient appleAuthClient;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AppleTokenCipher tokenCipher;
    private final WithdrawRetentionService withdrawRetentionService;

    public TokenResponse login(AppleLoginRequest request) {
        AppleIdentity identity = verifier.verify(request.identityToken(), request.nonce());

        // 탈퇴 시 폐기할 Apple refresh 토큰. 키 설정이 없거나 교환이 실패해도 로그인은 계속한다.
        // 암호화 키(APPLE_TOKEN_ENC_KEY)가 없으면 평문으로 남기지 않도록 아예 받지 않는다(prod 는 기동 시 확인).
        Optional<String> appleRefreshToken = Optional.empty();
        if (tokenCipher.isEnabled()) {
            appleRefreshToken = appleAuthClient.exchangeForRefreshToken(request.authorizationCode(), identity.clientId());
        } else if (request.authorizationCode() != null && !request.authorizationCode().isBlank()) {
            log.warn("APPLE_TOKEN_ENC_KEY 가 없어 Apple refresh 토큰을 받지 않습니다(탈퇴 시 Apple 토큰 폐기 불가).");
        }

        User user = userRepository.findBySocialTypeAndSocialId(SocialType.APPLE, identity.subject())
                .orElseGet(() -> createUser(identity, request.fullName()));

        if (appleRefreshToken.isPresent()) {
            user.linkAppleCredential(appleRefreshToken.get(), identity.clientId());
            userRepository.save(user);
        }

        return refreshTokenService.issueTokenPair(user.getId());
    }

    private User createUser(AppleIdentity identity, AppleLoginRequest.FullName fullName) {
        User user = User.builder()
                .socialType(SocialType.APPLE)
                .socialId(identity.subject())
                // 이메일은 받지 않는다(앱도 EMAIL scope를 요청하지 않음). 서비스에 쓰는 곳이 없어 최소 수집.
                .nickname(nicknameOf(fullName, identity.subject()))
                .build();
        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // 같은 사용자의 첫 로그인 요청이 동시에 두 번 들어온 경우 — 먼저 저장된 행을 쓴다.
            return userRepository.findBySocialTypeAndSocialId(SocialType.APPLE, identity.subject())
                    .orElseThrow(() -> e);
        }
        // 정지·신고 이력으로 탈퇴 기록이 남은 계정의 재가입이면 관리자 알림 + 기록 연결(가입은 막지 않음)
        withdrawRetentionService.onSignup(saved);
        return saved;
    }

    /**
     * Apple은 이름을 "처음 동의할 때" 한 번만 준다. 있으면 한국식(성+이름, 한글이 아니면 이름 성)으로 쓰고,
     * 없으면 "Apple 사용자 XXXX"(sub 해시 앞 4자리)로 만든다.
     */
    static String nicknameOf(AppleLoginRequest.FullName fullName, String subject) {
        String given = fullName == null ? null : trimToNull(fullName.givenName());
        String family = fullName == null ? null : trimToNull(fullName.familyName());
        String name;
        if (given != null && family != null) {
            boolean hangul = (given + family).codePoints()
                    .anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HANGUL);
            name = hangul ? family + given : given + " " + family;
        } else {
            name = given != null ? given : family;
        }
        if (name == null) {
            String suffix = AppleIdentityTokenVerifier.sha256Hex(subject).substring(0, 4).toUpperCase();
            return DEFAULT_NICKNAME_PREFIX + " " + suffix;
        }
        return name.length() > NICKNAME_MAX_LENGTH ? name.substring(0, NICKNAME_MAX_LENGTH) : name;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
