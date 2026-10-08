package com.hongmap.hongmapbackend.cafeteria;

import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuParser.ParseResult;
import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuService.UpsertResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 학식 메뉴 정기 가져오기. 학교는 이번 주(월~금) 메뉴를 한 번에 올려 두므로 하루 두 번이면 충분하다.
 * <ul>
 *   <li>평일 07:00, 10:30 KST(menu.fetch.cron-morning / cron-late-morning)</li>
 *   <li>월요일 12:00 KST 재시도(menu.fetch.cron-monday-retry) — 주 메뉴가 월요일 아침 늦게 올라오는 경우</li>
 *   <li>서버가 뜬 뒤 {@code menu.fetch.startup-delay-seconds} 초에 이번 주 메뉴가 하나도 없으면 한 번</li>
 * </ul>
 * menu.fetch.enabled=false 면 아무것도 하지 않는다(테스트 프로필). 한 번에 하나만 돈다(겹치면 건너뜀) — 서버 1대 기준.
 * 로그에는 건수만 남긴다(메뉴 텍스트·응답 본문은 남기지 않는다).
 */
@Slf4j
@Component
public class CafeteriaMenuFetchJob {

    private static final String KST = "Asia/Seoul";

    private final CafeteriaMenuClient client;
    private final CafeteriaMenuService service;
    private final TaskScheduler taskScheduler;
    private final boolean enabled;
    private final Duration startupDelay;
    private final AtomicBoolean running = new AtomicBoolean();

    public CafeteriaMenuFetchJob(CafeteriaMenuClient client, CafeteriaMenuService service, TaskScheduler taskScheduler,
                                 @Value("${menu.fetch.enabled:true}") boolean enabled,
                                 @Value("${menu.fetch.startup-delay-seconds:30}") long startupDelaySeconds) {
        this.client = client;
        this.service = service;
        this.taskScheduler = taskScheduler;
        this.enabled = enabled;
        this.startupDelay = Duration.ofSeconds(Math.max(0, startupDelaySeconds));
    }

    /** 한 번 가져온 결과(건수). */
    public record FetchResult(ParseResult parse, UpsertResult upsert) {
    }

    @Scheduled(cron = "${menu.fetch.cron-morning:0 0 7 * * MON-FRI}", zone = KST)
    public void morning() {
        runOnce("평일 07:00");
    }

    @Scheduled(cron = "${menu.fetch.cron-late-morning:0 30 10 * * MON-FRI}", zone = KST)
    public void lateMorning() {
        runOnce("평일 10:30");
    }

    @Scheduled(cron = "${menu.fetch.cron-monday-retry:0 0 12 * * MON}", zone = KST)
    public void mondayRetry() {
        runOnce("월요일 재시도");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleStartupCheck() {
        if (!enabled) {
            return;
        }
        taskScheduler.schedule(this::startupCheck, Instant.now().plus(startupDelay));
    }

    /** 이번 주 메뉴가 없을 때만 가져온다. */
    void startupCheck() {
        try {
            if (service.hasWeek(service.today())) {
                log.info("학식 메뉴: 이번 주 메뉴가 있어 시작 시 가져오기 건너뜀");
                return;
            }
        } catch (Exception e) {
            log.warn("학식 메뉴: 이번 주 메뉴 확인 실패 ({})", e.getClass().getSimpleName());
            return;
        }
        runOnce("서버 시작");
    }

    /**
     * 가져와서 저장한다. 꺼져 있거나 이미 도는 중이면 빈 값. 예외를 던지지 않는다(실패는 경고 로그).
     */
    public Optional<FetchResult> runOnce(String trigger) {
        if (!enabled) {
            return Optional.empty();
        }
        if (!running.compareAndSet(false, true)) {
            log.info("학식 메뉴 가져오기({}) 건너뜀 — 이미 진행 중", trigger);
            return Optional.empty();
        }
        try {
            String body = client.fetch();
            ParseResult parsed = CafeteriaMenuParser.parse(body);
            if (parsed.skippedUnknown() > 0 || parsed.skippedInvalid() > 0) {
                log.warn("학식 메뉴({}): 건너뛴 행 — 모르는 식당·끼니 {}건, 깨진·빈 행 {}건 (전체 {}건). 대응표 확인 필요할 수 있음",
                        trigger, parsed.skippedUnknown(), parsed.skippedInvalid(), parsed.totalRows());
            }
            if (parsed.menus().isEmpty()) {
                log.warn("학식 메뉴({}): 저장할 메뉴 0건 (응답 행 {}건)", trigger, parsed.totalRows());
            }
            UpsertResult upserted = service.upsert(parsed.menus());
            log.info("학식 메뉴 가져오기({}) 완료 — 메뉴 {}건(신규 {}, 갱신 {}), 응답 행 {}건",
                    trigger, parsed.menus().size(), upserted.inserted(), upserted.updated(), parsed.totalRows());
            return Optional.of(new FetchResult(parsed, upserted));
        } catch (CafeteriaMenuParser.ParseException e) {
            log.warn("학식 메뉴 가져오기({}) 실패 — 응답 형식: {}", trigger, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("학식 메뉴 가져오기({}) 중단됨", trigger);
        } catch (Exception e) {
            log.warn("학식 메뉴 가져오기({}) 실패 — {}: {}", trigger, e.getClass().getSimpleName(), e.getMessage());
        } finally {
            running.set(false);
        }
        return Optional.empty();
    }
}
