package com.hongmap.hongmapbackend.auth.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 카카오 로그인 진입(/oauth2/authorization/**) 때 {@code redirect_uri} 쿼리를 받아 세션에 적어 둔다.
 * 로그인이 끝나면 {@link OAuth2SuccessHandler}가 이 값으로 돌려보낸다 — 앱은 기본값
 * (hongikon://auth/callback), 웹 관리자는 https://hongikon.com/admin 처럼 자기 주소를 넘긴다.
 *
 * 허용 목록과 정확히 일치할 때만 저장한다(오픈 리다이렉트로 1회용 코드가 새는 것을 막기 위해).
 * OAuth2 인가 요청 자체도 세션에 저장되므로(state 검증), 콜백 때 같은 세션으로 돌아온다.
 */
public class OAuth2RedirectUriCaptureFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = OAuth2RedirectUriCaptureFilter.class.getName() + ".REDIRECT_URI";
    private static final String AUTHORIZATION_PATH_PREFIX = "/oauth2/authorization/";
    private static final String PARAMETER = "redirect_uri";

    private final OAuth2RedirectUriPolicy policy;

    public OAuth2RedirectUriCaptureFilter(OAuth2RedirectUriPolicy policy) {
        this.policy = policy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AUTHORIZATION_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requested = request.getParameter(PARAMETER);
        HttpSession session = request.getSession();
        if (requested != null && policy.isAllowed(requested)) {
            session.setAttribute(SESSION_ATTRIBUTE, requested);
        } else {
            // 이전 로그인 시도의 값이 남아 엉뚱한 곳으로 가지 않도록 매번 초기화한다.
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
        filterChain.doFilter(request, response);
    }
}
