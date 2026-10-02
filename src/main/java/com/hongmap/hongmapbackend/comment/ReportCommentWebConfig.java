package com.hongmap.hongmapbackend.comment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 정지 회원의 댓글 쓰기·신고를 막는다. PR #13 의 SuspendedUserInterceptor(빈 이름 suspendedUserInterceptor, POST/PUT/PATCH 만 403)가
 * 있으면 댓글 경로에도 건다 — #13 이 머지되면 코드 수정 없이 바로 적용된다. #13 전에는 정지 기능 자체가 없으니 아무것도 하지 않는다.
 * 댓글 지우기(DELETE)와 목록(GET)은 정지 중에도 된다(#13 과 같은 규칙).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ReportCommentWebConfig implements WebMvcConfigurer {

    static final String SUSPENDED_INTERCEPTOR_BEAN = "suspendedUserInterceptor";
    static final String[] WRITE_PATHS = {"/reports/*/comments", "/reports/*/comments/*/flags"};

    private final ApplicationContext context;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!context.containsBean(SUSPENDED_INTERCEPTOR_BEAN)) {
            log.info("SuspendedUserInterceptor 없음(#13 전) — 댓글 쓰기 정지 회원 차단은 #13 배포 뒤 켜진다.");
            return;
        }
        registry.addInterceptor(context.getBean(SUSPENDED_INTERCEPTOR_BEAN, HandlerInterceptor.class))
                .addPathPatterns(WRITE_PATHS);
    }
}
