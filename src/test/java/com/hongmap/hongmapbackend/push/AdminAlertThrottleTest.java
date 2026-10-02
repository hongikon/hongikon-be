package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.admin.AdminAlertEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 알림 묶음 규칙 — 종류마다 window에 한 번, 첫 건은 바로, 사이에 온 건은 window 끝에 "N건"으로.
 */
class AdminAlertThrottleTest {

    private static final Instant T0 = Instant.parse("2026-10-02T00:00:00Z");
    private final AdminAlertThrottle throttle = new AdminAlertThrottle(Duration.ofMinutes(2));

    @Test
    void 첫_건은_바로_보낸다() {
        AdminAlertThrottle.Decision d = throttle.offer(pending(1L, 10L), T0);
        assertThat(d.sendNow()).isNotNull();
        assertThat(d.sendNow().count()).isEqualTo(1);
        assertThat(d.sendNow().excludeUserId()).isEqualTo(10L);
        assertThat(d.flushAt()).isNull();
    }

    @Test
    void window_안에_온_건은_쌓았다가_끝날_때_묶어서_보낸다() {
        throttle.offer(pending(1L, 10L), T0);

        AdminAlertThrottle.Decision second = throttle.offer(pending(2L, 10L), T0.plusSeconds(10));
        assertThat(second.sendNow()).isNull();
        assertThat(second.flushAt()).isEqualTo(T0.plusSeconds(120));

        AdminAlertThrottle.Decision third = throttle.offer(pending(3L, 10L), T0.plusSeconds(20));
        assertThat(third.sendNow()).isNull();
        assertThat(third.flushAt()).as("이미 예약돼 있으면 다시 예약하지 않는다").isNull();

        AdminAlertThrottle.Batch batch = throttle.drain(pending(0L, 0L).type(), T0.plusSeconds(120));
        assertThat(batch.count()).isEqualTo(2);
        assertThat(batch.latest().targetId()).isEqualTo(3L);
        assertThat(batch.excludeUserId()).as("모두 같은 유저가 만들었으면 그 유저는 뺀다").isEqualTo(10L);

        // 묶음 발송도 window를 다시 시작한다.
        assertThat(throttle.offer(pending(4L, 10L), T0.plusSeconds(130)).sendNow()).isNull();
        assertThat(throttle.offer(pending(5L, 10L), T0.plusSeconds(241)).sendNow()).isNull();
    }

    @Test
    void 여러_유저가_섞인_묶음은_아무도_빼지_않는다() {
        throttle.offer(pending(1L, 10L), T0);
        throttle.offer(pending(2L, 10L), T0.plusSeconds(1));
        throttle.offer(pending(3L, 11L), T0.plusSeconds(2));

        assertThat(throttle.drain(pending(0L, 0L).type(), T0.plusSeconds(120)).excludeUserId()).isNull();
    }

    @Test
    void 쌓인_것이_없으면_drain은_null이고_window가_지나면_다시_바로_보낸다() {
        throttle.offer(pending(1L, 10L), T0);
        assertThat(throttle.drain(pending(0L, 0L).type(), T0.plusSeconds(120))).isNull();
        assertThat(throttle.offer(pending(2L, 10L), T0.plusSeconds(120)).sendNow()).isNotNull();
    }

    @Test
    void 종류마다_따로_센다() {
        throttle.offer(pending(1L, 10L), T0);
        assertThat(throttle.offer(AdminAlertEvent.feedback(7L, null), T0.plusSeconds(1)).sendNow()).isNotNull();
        assertThat(throttle.offer(AdminAlertEvent.reportFlagged(1L, 12L, "t", "b", 1), T0.plusSeconds(2)).sendNow())
                .isNotNull();
    }

    private static AdminAlertEvent pending(Long reportId, Long authorId) {
        return AdminAlertEvent.reportPending(reportId, authorId, "붕어빵 트럭", "홍문관", 1);
    }
}
