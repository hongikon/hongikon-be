package com.hongmap.hongmapbackend.push;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Scheduled 스레드가 하나뿐이면 오래 걸리는 정기 크롤링이 리마인드·Apple 재시도를 막는다.
 * 한 작업이 붙잡고 있는 동안에도 다른 예약 작업이 돈다는 것을 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchedulingPoolTest {

    @Autowired ThreadPoolTaskScheduler taskScheduler;

    @Test
    void 오래_걸리는_예약_작업이_있어도_다른_예약_작업이_돈다() throws Exception {
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isGreaterThanOrEqualTo(2);

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch otherRan = new CountDownLatch(1);
        taskScheduler.execute(() -> {
            try {
                release.await(5, TimeUnit.SECONDS); // 크롤링처럼 오래 붙잡는 작업
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        taskScheduler.execute(otherRan::countDown);
        try {
            assertThat(otherRan.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
        }
    }
}
