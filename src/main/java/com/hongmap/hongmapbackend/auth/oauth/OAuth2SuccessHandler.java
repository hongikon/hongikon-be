package com.hongmap.hongmapbackend.auth.oauth;

import com.hongmap.hongmapbackend.auth.exchange.AuthorizationCodeStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        log.info("OAuth2SuccessHandler 진입, principal={}", authentication.getPrincipal());

        try {
            log.info("principal을 CustomOAuth2User로 캐스팅 시도");
            CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
            log.info("캐스팅 완료, userId={}", principal.getUserId());

            String code = authorizationCodeStore.issue(principal.getUserId());

            log.info("redirect target={}", redirectUri);
            String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("code", code)
                    .build()
                    .toUriString();

            log.info("최종 리다이렉트 URL={}", targetUrl);
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
