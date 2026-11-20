package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 11-20: 일정·마감처럼 학기마다 바뀌는 질문이면 고른 안내가 낱말을 다 덮어도 최근 공지를 함께 넘긴다(뉴스 탭 새 공지가 답에 안 나왔다). */
class AskRecentNoticesTest {

    private static AskLiveData.NoticeHit hit(long id, String title) {
        return new AskLiveData.NoticeHit(new AskLiveData.Reference("notice:" + id, title, "https://example.test/" + id, "2026-11-10"), "학사");
    }

    private static AskRouteService service(AskLiveData liveData) {
        return new AskRouteService(mock(AskCatalog.class), mock(AskLlmClient.class), liveData, mock(AskRetriever.class),
                mock(AskRuleGraph.class), Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 시간을_묻는_질문이면_시간_낱말을_빼고_최근_공지를_찾아_제목이_맞는_것만_넘긴다() {
        AskLiveData liveData = mock(AskLiveData.class);
        when(liveData.recentNotices(anyString(), eq(AskLiveData.RECENT_NOTICE_DAYS), anyInt())).thenReturn(List.of(
                hit(1, "2026학년도 동계 계절학기 수강신청 안내"),
                hit(2, "장학금 신청 안내"),
                hit(3, "계절학기 개설과목 공지")));

        List<AskLiveData.NoticeHit> recent = service(liveData).recentNotices("계절학기 수강신청 언제야");

        // 제목에 질문 낱말(계절학기·수강신청)이 하나라도 있는 것만, 최대 2개
        assertThat(recent).extracting(h -> h.reference().ref()).containsExactly("notice:1", "notice:3");
        verify(liveData).recentNotices(eq("계절학기 수강신청"), eq(AskLiveData.RECENT_NOTICE_DAYS), anyInt());
    }

    @Test
    void 시간을_묻지_않으면_최근_공지를_찾지_않는다() {
        AskLiveData liveData = mock(AskLiveData.class);
        assertThat(service(liveData).recentNotices("복수전공 신청 자격이 뭐야")).isEmpty();
        verify(liveData, never()).recentNotices(anyString(), anyInt(), anyInt());
    }

    @Test
    void 찾기에_실패해도_빈_목록으로_답을_이어_쓴다() {
        AskLiveData liveData = mock(AskLiveData.class);
        when(liveData.recentNotices(anyString(), anyInt(), anyInt())).thenThrow(new IllegalStateException("db"));
        assertThat(service(liveData).recentNotices("휴학 신청 기간")).isEmpty();
    }
}
