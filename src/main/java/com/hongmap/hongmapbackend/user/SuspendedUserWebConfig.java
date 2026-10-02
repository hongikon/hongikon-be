package com.hongmap.hongmapbackend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class SuspendedUserWebConfig implements WebMvcConfigurer {

    private final SuspendedUserInterceptor suspendedUserInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // POST/PUT/PATCH 만 검사한다(인터셉터). 본인 제보 삭제(DELETE /reports/{id})는 정지 중에도 허용.
        registry.addInterceptor(suspendedUserInterceptor)
                .addPathPatterns("/reports", "/reports/*/flags", "/reports/images", "/feedback",
                        "/users/me/nickname");
    }
}
