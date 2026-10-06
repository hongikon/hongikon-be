package com.hongmap.hongmapbackend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시각을 주입받는 컴포넌트용 Clock(UTC — 서버 기준 시각, HongmapBackendApplication 참고).
 * 테스트는 @Primary 로 바꿔 끼워 방해 금지 시간·빈도 제한을 시간 흐름대로 검증한다(NewReportDigestTest).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
