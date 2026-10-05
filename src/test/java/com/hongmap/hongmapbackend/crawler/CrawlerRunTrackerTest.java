package com.hongmap.hongmapbackend.crawler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 관리자 대시보드용 마지막 실행 요약(요청 수·소요 시간·실패/건너뛴 게시판). */
class CrawlerRunTrackerTest {

    @Test
    void 마지막_실행_요약을_스냅샷에_담고_예외로_끝나면_비운다() {
        CrawlerService service = mock(CrawlerService.class);
        when(service.crawlAll())
                .thenReturn(new CrawlResult(3, 48, List.of("건축학부"), List.of("도시공학과"), List.of(), 52, 31_000))
                .thenThrow(new IllegalStateException("boom"));
        CrawlerRunTracker tracker = new CrawlerRunTracker(service);

        assertThat(tracker.run(CrawlerRunTracker.Trigger.MANUAL)).isEqualTo(3);
        CrawlerRunTracker.Snapshot s = tracker.snapshot();
        assertThat(s.lastSavedCount()).isEqualTo(3);
        assertThat(s.lastRequestCount()).isEqualTo(52);
        assertThat(s.lastDurationMs()).isEqualTo(31_000);
        assertThat(s.lastFailedBoards()).containsExactly("건축학부");
        assertThat(s.lastSkippedBoards()).containsExactly("도시공학과");

        assertThatThrownBy(() -> tracker.run(CrawlerRunTracker.Trigger.SCHEDULED)).isInstanceOf(IllegalStateException.class);
        assertThat(tracker.snapshot().lastRequestCount()).isNull();
        assertThat(tracker.snapshot().lastError()).contains("boom");
    }
}
