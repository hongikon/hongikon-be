package com.hongmap.hongmapbackend.auth.oauth;

import com.hongmap.hongmapbackend.auth.exchange.AuthorizationCodeStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthorizationCodeStore authorizationCodeStore;
    private final OAuth2RedirectUriPolicy redirectUriPolicy;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        try {
            CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();

            // 로그인 진입 때 OAuth2RedirectUriCaptureFilter가 세션에 적어 둔 주소(웹 관리자 등). 없으면 앱 딥링크.
            HttpSession session = request.getSession(false);
            String requested = session == null ? null
                    : (String) session.getAttribute(OAuth2RedirectUriCaptureFilter.SESSION_ATTRIBUTE);
            String codeChallenge = session == null ? null
                    : (String) session.getAttribute(OAuth2RedirectUriCaptureFilter.CODE_CHALLENGE_ATTRIBUTE);
            if (session != null) {
                session.removeAttribute(OAuth2RedirectUriCaptureFilter.SESSION_ATTRIBUTE);
                session.removeAttribute(OAuth2RedirectUriCaptureFilter.CODE_CHALLENGE_ATTRIBUTE);
            }

            // code_challenge 를 보낸 앱이면 같은 앱만 code 를 토큰으로 바꿀 수 있다(PKCE).
            String code = authorizationCodeStore.issue(principal.getUserId(), codeChallenge);
            String redirectUri = redirectUriPolicy.resolve(requested);

            // 1회용 코드가 로그에 남지 않도록 쿼리를 붙이기 전 주소만 기록한다.
            log.info("카카오 로그인 성공: userId={}, redirect target={}, pkce={}",
                    principal.getUserId(), redirectUri, codeChallenge != null);
            String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("code", code)
                    .build()
                    .toUriString();

            response.sendRedirect(targetUrl);
        } catch (Exception e) {
            log.error("SuccessHandler에서 예외 발생", e);
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IllegalStateException("OAuth2 로그인 성공 처리 중 오류가 발생했습니다.", e);
        }
    }
}
