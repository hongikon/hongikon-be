package com.hongmap.hongmapbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class HongmapBackendApplication {

	/*
	 * 서버 시각 기준을 UTC 로 못 박는다. 앱은 제보 시각을 UTC(toISOString)로 보내고, 응답의 존 없는
	 * LocalDateTime 도 UTC 로 읽는다(hongikon-fe utils/serverTime.ts). JVM 기본 시간대가 KST 면
	 * LocalDateTime.now() 가 9시간 앞서 @Future·endsAt 비교가 어긋나 모든 제보가 400 이 된다.
	 * 실행 환경(TZ 환경변수, 로컬 맥)에 상관없이 같게 동작하도록 클래스 로딩 시점에 고정한다(테스트 포함).
	 */
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(HongmapBackendApplication.class, args);
	}

}
