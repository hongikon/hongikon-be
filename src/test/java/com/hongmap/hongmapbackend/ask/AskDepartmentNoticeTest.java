package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 11-14: 질문 속 학과 게시판에서 제목이 맞는 공지를 미리 읽는다('컴공 중간고사 일정' → 컴공 '중간고사 일정 안내'). */
class AskDepartmentNoticeTest {

    private static AskLiveData.NoticeHit hit(long id, String title) {
        return new AskLiveData.NoticeHit(new AskLiveData.Reference("notice:" + id, title, "https://example.test/" + id, "2026-09-21"), "컴퓨터공학과");
    }

    private static AskRouteService service(AskLiveData liveData) {
        return new AskRouteService(mock(AskCatalog.class), mock(AskLlmClient.class), liveData, mock(AskRetriever.class),
                mock(AskRuleGraph.class), Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 학과_게시판에서_제목이_맞는_공지를_읽는다() {
        AskLiveData liveData = mock(AskLiveData.class);
        when(liveData.searchNotices(anyString(), eq("컴퓨터공학과"))).thenReturn(List.of(
                hit(1, "2026학년도 동계 현장실습 일정 안내"),
                hit(2, "[필독] 2026-2학기 중간고사 일정 안내")));
        AskLiveData.NoticeBody body = new AskLiveData.NoticeBody(hit(2, "[필독] 2026-2학기 중간고사 일정 안내").reference(), "컴퓨터공학과", "10/20~10/24 …");
        when(liveData.readNotice(2L)).thenReturn(Optional.of(body));

        List<AskLiveData.NoticeBody> notices = service(liveData).departmentNotices("컴공 중간고사 일정 알려줘");

        assertThat(notices).containsExactly(body);
        verify(liveData, never()).readNotice(1L);
    }

    @Test
    void 언제_같은_물음말은_빼고_맞춘다() {
        AskLiveData liveData = mock(AskLiveData.class);
        when(liveData.searchNotices(anyString(), eq("컴퓨터공학과"))).thenReturn(List.of(hit(2, "[필독] 2026-2학기 중간고사 일정 안내")));
        when(liveData.readNotice(2L)).thenReturn(Optional.of(new AskLiveData.NoticeBody(hit(2, "x").reference(), "컴퓨터공학과", "본문")));

        assertThat(service(liveData).departmentNotices("컴공 중간고사 언제야?")).hasSize(1);
    }

    @Test
    void 제목이_맞지_않거나_학과가_없으면_읽지_않는다() {
        AskLiveData liveData = mock(AskLiveData.class);
        when(liveData.searchNotices(anyString(), eq("컴퓨터공학과"))).thenReturn(List.of(hit(1, "2026학년도 동계 현장실습 안내")));

        assertThat(service(liveData).departmentNotices("컴공 예비군 전입신고")).isEmpty();
        assertThat(service(liveData).departmentNotices("예비군 전입신고 어떻게 해")).isEmpty();
        verify(liveData, never()).readNotice(anyLong());
    }

    @Test
    void 제목_비교는_띄어쓰기_가운뎃점을_무시한다() {
        assertThat(AskRouteService.titleMatches("중간 고사 일정 안내", List.of("중간고사", "일정"))).isEqualTo(2);
        assertThat(AskRouteService.titleMatches("학‧예술상 안내", List.of("학예술상"))).isEqualTo(1);
        assertThat(AskRouteService.noticeId("notice:42")).contains(42L);
        assertThat(AskRouteService.noticeId("page:x")).isEmpty();
    }
}
