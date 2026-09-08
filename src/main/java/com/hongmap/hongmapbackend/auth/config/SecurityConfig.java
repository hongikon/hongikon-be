package com.hongmap.hongmapbackend.auth.config;

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

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/oauth2/**", "/login/oauth2/**", "/auth/token/exchange",
                            "/auth/reissue", "/auth/logout").permitAll();
                    // AuthTestController와 동일하게 local 프로필에서만 인증 없이 열어준다.
                    if (environment.acceptsProfiles(Profiles.of("local"))) {
                        auth.requestMatchers("/auth/test-token").permitAll();
                    }
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/status").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/reports").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/buildings", "/buildings/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/places", "/places/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/news", "/news/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/departments").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/partners", "/partners/**").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/routes/search").permitAll();
                    auth.anyRequest().authenticated();
                })
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
