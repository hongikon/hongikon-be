package com.hongmap.hongmapbackend.auth.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 카카오 로그인 후 1회용 코드를 들고 돌아갈 수 있는 주소 목록. 기본값(app.oauth2.redirect-uri)은 앱 딥링크이고,
 * 웹 관리자 화면 주소는 app.oauth2.allowed-redirect-uris 에 정확한 문자열로 등록해야 한다.
 */
@Component
public class OAuth2RedirectUriPolicy {

    private final String defaultRedirectUri;
    private final List<String> allowedRedirectUris;

    public OAuth2RedirectUriPolicy(@Value("${app.oauth2.redirect-uri}") String defaultRedirectUri,
                                   @Value("${app.oauth2.allowed-redirect-uris:}") List<String> allowedRedirectUris) {
        this.defaultRedirectUri = defaultRedirectUri;
        this.allowedRedirectUris = allowedRedirectUris.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean isAllowed(String redirectUri) {
        return defaultRedirectUri.equals(redirectUri) || allowedRedirectUris.contains(redirectUri);
    }

    /** 요청한 주소가 허용 목록에 있으면 그 주소, 아니면 앱 기본 주소. */
    public String resolve(String requested) {
        return requested != null && isAllowed(requested) ? requested : defaultRedirectUri;
    }
}
