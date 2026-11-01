package com.hongmap.hongmapbackend.ask;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 물어보기 캐시 데우기(ask.llm.warm.*). 5분마다 확인해, LLM 호출이 idle 분 넘게 없었으면
 * {@link AskLlmClient#warmIfIdle} 로 앞부분 캐시(고르기 카탈로그·답 쓰기 지시, 약 9.4만 토큰)를 살려 둔다.
 * 아침 첫 질문·한가한 시간 질문도 캐시를 읽게(새로 쓰면 질문 1개 비용이 약 4배). 1시간 캐시일 때만 동작한다.
 */
@Component
class AskCacheWarmer {

    private static final Logger log = LoggerFactory.getLogger(AskCacheWarmer.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AskLlmClient llm;
    private final boolean enabled;
    private final int startHour;
    private final int endHour;
    private final Duration idle;

    AskCacheWarmer(AskLlmClient llm,
                   @Value("${ask.llm.warm.enabled:true}") boolean enabled,
                   @Value("${ask.llm.warm.hours:0-24}") String hours,
                   @Value("${ask.llm.warm.idle-minutes:50}") long idleMinutes) {
        this.llm = llm;
        this.enabled = enabled;
        String[] range = hours.trim().split("-");
        if (range.length != 2) {
            throw new IllegalArgumentException("ask.llm.warm.hours 는 '시작-끝'(예: 7-24) 이어야 한다: " + hours);
        }
        this.startHour = Integer.parseInt(range[0].trim());
        this.endHour = Integer.parseInt(range[1].trim());
        if (startHour < 0 || endHour > 24 || startHour >= endHour) {
            throw new IllegalArgumentException("ask.llm.warm.hours 범위가 잘못됐다: " + hours);
        }
        // 1시간 캐시가 끝나기 전에 데워야 한다 — 확인 간격(5분)만큼 여유를 둔다.
        if (idleMinutes < 1 || idleMinutes > 55) {
            throw new IllegalArgumentException("ask.llm.warm.idle-minutes 는 1~55 여야 한다: " + idleMinutes);
        }
        this.idle = Duration.ofMinutes(idleMinutes);
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    void tick() {
        if (!enabled || !inHours(LocalTime.now(SEOUL))) {
            return;
        }
        if (llm.warmIfIdle(idle)) {
            log.info("물어보기 캐시 데우기");
        }
    }

    boolean inHours(LocalTime now) {
        return now.getHour() >= startHour && now.getHour() < endHour;
    }
}
