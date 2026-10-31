package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskRouteRequest;
import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuService;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaDayResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaMealResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaRestaurantResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 학식 질문이면 서버가 메뉴를 미리 꺼내 답 쓰기에 넘긴다(10-31, 링크만 주던 답). */
class AskCafeteriaQuestionTest {

    /** 2026-10-31 토요일. */
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 31);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 28);

    // ---- 학식 질문 알아보기 ----

    @Test
    void 학식_낱말이_있으면_날짜를_읽고_없으면_빈_목록() {
        assertThat(AskCafeteriaQuestion.dates("오늘 학식 뭐야", SATURDAY)).containsExactly(SATURDAY);
        assertThat(AskCafeteriaQuestion.dates("학식 메뉴", SATURDAY)).containsExactly(SATURDAY);
        assertThat(AskCafeteriaQuestion.dates("내일 학생식당 점심", SATURDAY)).containsExactly(SATURDAY.plusDays(1));
        assertThat(AskCafeteriaQuestion.dates("모레 식단", SATURDAY)).containsExactly(SATURDAY.plusDays(2));
        assertThat(AskCafeteriaQuestion.dates("오늘 점심 뭐 나와?", SATURDAY)).containsExactly(SATURDAY);
        // 끼니 낱말만 있고 날짜 낱말이 없으면 학식 질문으로 보지 않는다('점심 먹을 곳 추천' 같은 제휴 질문).
        assertThat(AskCafeteriaQuestion.dates("점심 먹을 곳 추천", SATURDAY)).isEmpty();
        // '입학식'의 '학식'은 아니다.
        assertThat(AskCafeteriaQuestion.dates("입학식 언제야", SATURDAY)).isEmpty();
        assertThat(AskCafeteriaQuestion.dates("휴학 어떻게 해요", SATURDAY)).isEmpty();
        assertThat(AskCafeteriaQuestion.dates(null, SATURDAY)).isEmpty();
    }

    @Test
    void 요일은_다가오는_그날이고_다음_주면_한_주_뒤() {
        assertThat(AskCafeteriaQuestion.dates("금요일 학식", WEDNESDAY)).containsExactly(LocalDate.of(2026, 10, 30));
        assertThat(AskCafeteriaQuestion.dates("다음 주 금요일 학식", WEDNESDAY)).containsExactly(LocalDate.of(2026, 11, 6));
        // 토요일에 '다음 주 월요일'은 이틀 뒤 월요일이다(이번 주 월요일은 지났다).
        assertThat(AskCafeteriaQuestion.dates("다음주 월요일 학식", SATURDAY)).containsExactly(LocalDate.of(2026, 11, 2));
        assertThat(AskCafeteriaQuestion.dates("월요일 학식", SATURDAY)).containsExactly(LocalDate.of(2026, 11, 2));
    }

    @Test
    void 이번_주를_물으면_오늘부터_일요일까지() {
        assertThat(AskCafeteriaQuestion.dates("이번 주 학식", WEDNESDAY))
                .containsExactly(WEDNESDAY, WEDNESDAY.plusDays(1), WEDNESDAY.plusDays(2), WEDNESDAY.plusDays(3), WEDNESDAY.plusDays(4));
    }

    // ---- 메뉴가 없는 날 ----

    private static CafeteriaDayResponse day(LocalDate date, boolean served) {
        List<CafeteriaMealResponse> meals = served
                ? List.of(new CafeteriaMealResponse("점심A", "11:30~14:00", "5,800원", List.of("백미밥", "어묵우동"), false))
                : List.of();
        return new CafeteriaDayResponse(date, "홍익대", "https://www.hongik.ac.kr/kr/life/seoul-cafeteria.do", null,
                List.of(new CafeteriaRestaurantResponse("student", null, "학생식당", meals)));
    }

    private static AskLiveData liveData() {
        CafeteriaMenuService service = mock(CafeteriaMenuService.class);
        when(service.today()).thenReturn(SATURDAY);
        when(service.day(any())).thenAnswer(call -> {
            LocalDate date = call.getArgument(0);
            return day(date, date.getDayOfWeek().getValue() <= 5);
        });
        return new AskLiveData(null, service, null, null);
    }

    @Test
    void 그날_메뉴가_없으면_다음_메뉴가_있는_날을_붙인다() {
        AskLiveData.DayMenu menu = liveData().cafeteria("today");
        assertThat(menu.served()).isFalse();
        assertThat(menu.reference().ref()).isEqualTo("cafeteria:2026-10-31");
        assertThat(menu.text()).contains("(이날 저장된 메뉴가 없어요)")
                .contains("[다음 메뉴가 있는 날] 2026-11-02(월)")
                .contains("점심A 11:30~14:00 5,800원: 백미밥, 어묵우동");
    }

    @Test
    void 메뉴가_있는_날이나_이번_주를_볼_때는_다음_날을_붙이지_않는다() {
        AskLiveData live = liveData();
        AskLiveData.DayMenu friday = live.cafeteria(LocalDate.of(2026, 10, 30), true);
        assertThat(friday.served()).isTrue();
        assertThat(friday.text()).doesNotContain("[다음 메뉴가 있는 날]");
        assertThat(live.cafeteria(SATURDAY, false).text()).doesNotContain("[다음 메뉴가 있는 날]");
    }

    // ---- 물어보기에 끼우기 ----

    private static AskRouteService service(AskLlmClient llm, AskLiveData live) {
        AskCatalog catalog = AskRuleGraphTest.catalog("test-v1");
        when(llm.classify(anyString(), any())).thenReturn(new AskLlmClient.Classification(AskScope.SCHOOL, List.of("retake/apply")));
        AskLlmClient.Answer answer = new AskLlmClient.Answer("답", true, List.of());
        when(llm.answer(anyList(), anyString(), any())).thenReturn(answer);
        when(llm.answer(anyList(), anyList(), anyString(), anyList(), any())).thenReturn(answer);
        return new AskRouteService(catalog, llm, live, mock(AskRetriever.class),
                new AskRuleGraph(catalog, (AskRuleGraph.GraphFile) null, false, 8, 2),
                // 2026-10-31 12:00 한국 시간(토요일).
                Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), ZoneOffset.UTC));
    }

    /** 학식 메뉴만 진짜로 읽고 공지 검색 등은 빈 결과인 가짜. */
    private static AskLiveData menuOnly() {
        AskLiveData real = liveData();
        AskLiveData live = mock(AskLiveData.class);
        when(live.cafeteria(any(LocalDate.class), any(Boolean.class)))
                .thenAnswer(call -> real.cafeteria((LocalDate) call.getArgument(0), (boolean) call.getArgument(1)));
        return live;
    }

    @Test
    void 학식_질문이면_메뉴를_미리_꺼내_답_쓰기에_넘긴다() {
        AskLlmClient llm = mock(AskLlmClient.class);
        service(llm, menuOnly()).route(new AskRouteRequest("오늘 학식 뭐야", null, null), AskProgress.NONE);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AskLiveData.DayMenu>> menus = ArgumentCaptor.forClass(List.class);
        verify(llm).answer(anyList(), anyList(), anyString(), menus.capture(), any());
        verify(llm, never()).answer(anyList(), anyString(), any());
        assertThat(menus.getValue()).hasSize(1);
        assertThat(menus.getValue().get(0).reference().ref()).isEqualTo("cafeteria:2026-10-31");
        assertThat(menus.getValue().get(0).text()).contains("[다음 메뉴가 있는 날] 2026-11-02(월)");
    }

    @Test
    void 학식_질문이_아니면_지금_경로_그대로() {
        AskLlmClient llm = mock(AskLlmClient.class);
        service(llm, menuOnly()).route(new AskRouteRequest("휴학 어떻게 해요", null, null), AskProgress.NONE);
        verify(llm).answer(eq(List.of("retake/apply")), anyString(), any());
        verify(llm, never()).answer(anyList(), anyList(), anyString(), anyList(), any());
    }

    @Test
    void 메뉴를_못_읽으면_도구에_맡기고_지금_경로로() {
        AskLlmClient llm = mock(AskLlmClient.class);
        AskLiveData broken = mock(AskLiveData.class);
        when(broken.cafeteria(any(LocalDate.class), any(Boolean.class))).thenThrow(new IllegalStateException("db down"));
        service(llm, broken).route(new AskRouteRequest("오늘 학식 뭐야", null, null), AskProgress.NONE);
        verify(llm).answer(anyList(), anyString(), any());
    }
}
