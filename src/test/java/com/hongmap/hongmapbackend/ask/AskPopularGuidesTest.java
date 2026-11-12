package com.hongmap.hongmapbackend.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AskPopularGuidesTest {

    private final AskCatalog catalog = new AskCatalog(new AskCatalog.CatalogFile("v", List.of(
            new AskCatalog.Entry("leave/start", "question", "휴학"),
            new AskCatalog.Entry("wifi/start", "answer", "와이파이"),
            new AskCatalog.Entry("mail/start", "answer", "메일"))), new AskCatalog.SourcesFile("v", Map.of()));

    private static List<Object[]> uses(String nodeIds, int conversations) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < conversations; i++) {
            rows.add(new Object[]{"c" + nodeIds + i, nodeIds});
            rows.add(new Object[]{"c" + nodeIds + i, nodeIds}); // 같은 대화에서 또 물어도 한 번으로 센다
        }
        return rows;
    }

    @Test
    void 서로_다른_대화_5개_이상인_안내만_많은_순으로() {
        AskPopularGuides guides = new AskPopularGuides(mock(AskConversationLogRepository.class), catalog);
        List<Object[]> rows = new ArrayList<>();
        rows.addAll(uses("wifi/start", 5));
        rows.addAll(uses("leave/start,none", 7));
        rows.addAll(uses("mail/start", 4));
        rows.addAll(uses("gone/start", 9)); // 카탈로그에 없는 id(옛 안내)는 뺀다
        rows.add(new Object[]{null, "mail/start"}); // 대화 id 없는 기록은 세지 않는다
        assertThat(guides.rank(rows)).containsExactly("leave/start", "wifi/start");
    }

    @Test
    void 한_시간_안에는_다시_세지_않는다() {
        AskConversationLogRepository repository = mock(AskConversationLogRepository.class);
        when(repository.findGuideUses(any(), any())).thenReturn(uses("wifi/start", 5));
        AskPopularGuides guides = new AskPopularGuides(repository, catalog, Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC));
        assertThat(guides.ids()).containsExactly("wifi/start");
        assertThat(guides.ids()).containsExactly("wifi/start");
        verify(repository, times(1)).findGuideUses(any(), any());
    }
}
