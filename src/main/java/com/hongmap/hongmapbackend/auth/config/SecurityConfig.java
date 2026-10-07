package com.hongmap.hongmapbackend.auth.config;

import com.hongmap.hongmapbackend.auth.oauth.OAuth2RedirectUriCaptureFilter;
import com.hongmap.hongmapbackend.auth.oauth.OAuth2RedirectUriPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import com.hongmap.hongmapbackend.auth.jwt.JwtAuthenticationFilter;
import com.hongmap.hongmapbackend.auth.oauth.CustomOAuth2UserService;
import com.hongmap.hongmapbackend.auth.oauth.OAuth2SuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdminAccessChecker adminAccessChecker;
    private final OAuth2RedirectUriPolicy oAuth2RedirectUriPolicy;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> {
                    // 처리 중 예외는 /error 로 포워드된다. 여기가 인증을 요구하면 400·404·500 이 전부 빈 401 로
                    // 바뀌어, 앱이 "로그인 만료"로 오인하고 토큰 재발급 후 요청을 다시 보낸다.
                    auth.requestMatchers("/error").permitAll();
                    auth.requestMatchers("/oauth2/**", "/login/oauth2/**", "/auth/token/exchange",
                            "/auth/reissue", "/auth/logout").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/auth/apple").permitAll();
                    // AuthTestController와 동일하게 local 프로필에서만 인증 없이 열어준다.
                    if (environment.acceptsProfiles(Profiles.of("local"))) {
                        auth.requestMatchers("/auth/test-token").permitAll();
                    }
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/status").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/reports").permitAll();
                    // 제보 댓글 목록은 게스트도 본다(쓰기·삭제·신고는 아래 authenticated).
                    auth.requestMatchers(HttpMethod.GET, "/reports/*/comments", "/reports/*/comments/*/replies").permitAll();
                    // 제보 조회 수 기록은 게스트도(설치 id 로 하루 한 번). 🔥·관심·👍 은 아래 authenticated.
                    auth.requestMatchers(HttpMethod.POST, "/reports/*/views").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/buildings", "/buildings/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/places", "/places/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/news", "/news/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/departments").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/partners", "/partners/**").permitAll();
                    // 앱 지도 데이터(건물·편의시설·제휴업체). 공개 정보만 담는다 — 편집은 /admin/map/** (ADMIN).
                    auth.requestMatchers(HttpMethod.GET, "/map/data").permitAll();
                    // 학식 메뉴(학교 홈페이지 공개 메뉴를 서버가 가져와 둔 것). 조회만.
                    auth.requestMatchers(HttpMethod.GET, "/cafeteria/menus", "/cafeteria/menus/week").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/routes/search").permitAll();
                    // 문의하기는 비로그인(게스트)도 보낼 수 있다. 토큰이 있으면 작성자로 연결된다.
                    auth.requestMatchers(HttpMethod.POST, "/feedback").permitAll();
                    // 운영용 엔드포인트는 users.role = ADMIN 만. 비로그인은 401, 일반 사용자는 403.
                    auth.requestMatchers("/admin/**", "/crawler/**").access((authentication, context) ->
                            new AuthorizationDecision(adminAccessChecker.isAdmin(authentication.get())));
                    // 제휴업체 등록/삭제도 운영 작업이라 ADMIN 만. (조회 GET 은 위에서 permitAll)
                    auth.requestMatchers(HttpMethod.POST, "/partners").access((authentication, context) ->
                            new AuthorizationDecision(adminAccessChecker.isAdmin(authentication.get())));
                    auth.requestMatchers(HttpMethod.DELETE, "/partners/**").access((authentication, context) ->
                            new AuthorizationDecision(adminAccessChecker.isAdmin(authentication.get())));
                    auth.anyRequest().authenticated();
                })
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler))
                // 인증 실패(401) 때 원래 요청을 세션에 저장하지 않는다. 성공 핸들러가 저장된 요청을 쓰지 않는데도
                // 기본값이면 401 응답마다 JSESSIONID 세션이 새로 생겨, 비로그인 요청만으로 서버 메모리를 채울 수 있었다.
                // 세션은 카카오 로그인 진행 중(state·redirect_uri 보관)에만 생긴다.
                .requestCache(requestCache -> requestCache.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(new OAuth2RedirectUriCaptureFilter(oAuth2RedirectUriPolicy),
                        OAuth2AuthorizationRequestRedirectFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                // 로컬 개발 (웹 / Expo 웹 / 안드로이드 에뮬레이터)
                "http://localhost:8080",
                "http://localhost:8081",
                "http://10.0.2.2:8080",
                // 프론트 배포 도메인
                "https://hongikon.com",
                "https://www.hongikon.com",
                "https://hongmap12.netlify.app",
                // 백엔드 자체 도메인
                "https://api.hongikon.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // JWT는 Authorization 헤더로, refresh 토큰은 body로 주고받으므로 쿠키 전송이 필요 없다.
        config.setAllowCredentials(false);
        // GET /map/data 의 ETag 를 웹 앱이 읽어 다음 요청의 If-None-Match 로 보낸다(노출하지 않으면 JS 에서 안 보인다).
        config.setExposedHeaders(List.of("ETag"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
