package com.hongmap.hongmapbackend.push;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 새 소식 푸시의 "오래된 글" 기준일은 한국 날짜로 센다(서버 JVM 은 UTC). */
class NewsPushCutoffTest {

    @Test
    void KST_새벽에도_한국_날짜_기준으로_N일_전_0시다() {
        // 2026-10-05 16:00 UTC = 2026-10-06 01:00 KST. UTC 날짜(10-05)로 세면 10-02 0시가 돼 하루 더 지난 글까지 푸시됐다.
        Instant kstEarlyMorning = Instant.parse("2026-10-05T16:00:00Z");
        assertThat(NewsPushDispatcher.publishCutoff(kstEarlyMorning, 3))
                .isEqualTo(LocalDateTime.of(2026, 10, 3, 0, 0));
    }

    @Test
    void KST_낮에는_UTC와_날짜가_같다() {
        Instant kstNoon = Instant.parse("2026-10-06T03:00:00Z"); // 12:00 KST
        assertThat(NewsPushDispatcher.publishCutoff(kstNoon, 3)).isEqualTo(LocalDateTime.of(2026, 10, 3, 0, 0));
    }
}
