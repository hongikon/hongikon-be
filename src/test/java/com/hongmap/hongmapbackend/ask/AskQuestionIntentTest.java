package com.hongmap.hongmapbackend.ask;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongmap.hongmapbackend.ask.dto.AskRouteResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class AskQuestionIntentTest {

    @Test
    void 방법_절차를_물으면_true() {
        for (String q : List.of("홍아람 어떻게 지원해?", "휴학 신청 방법", "기숙사 신청하려면?", "장학금 서류 뭐 내?",
                "전과 지원 자격", "동아리 가입하고 싶어", "학생증 어디서 받아?", "복학 절차 알려줘")) {
            assertThat(AskQuestionIntent.asksHow(q)).as(q).isTrue();
        }
    }

    @Test
    void 이름_소개_일정만_물으면_false() {
        for (String q : List.of("홍아람", "홍아람이 뭐야", "휴학 신청 언제야?", "동아리 지원 기간", "학식 메뉴", "계절학기 등록금 얼마야")) {
            assertThat(AskQuestionIntent.asksHow(q)).as(q).isFalse();
        }
    }

    @Test
    void 방법을_묻지_않았으면_신청_방법_섹션만_뺀다() {
        AskRouteResponse.Section apply = new AskRouteResponse.Section("신청 방법", List.of("누가: 재학생"), true, null);
        AskRouteResponse.Section caution = new AskRouteResponse.Section("꼭 확인할 점", List.of("마감 11.11"), false, null);
        assertThat(AskQuestionIntent.filter("홍아람", List.of(apply, caution))).containsExactly(caution);
        assertThat(AskQuestionIntent.filter("홍아람 어떻게 지원해?", List.of(apply, caution))).containsExactly(apply, caution);
    }

    @Test
    void 방법을_물었는데_제목이_빈_순서_목록이면_신청_방법으로_채운다() {
        AskRouteResponse.Section untitled = new AskRouteResponse.Section("", List.of("누가: 재학생", "언제: 11.2~11.11"), true, null);
        assertThat(AskQuestionIntent.filter("홍아람 어떻게 지원해?", List.of(untitled)).get(0).title()).isEqualTo("신청 방법");
        assertThat(AskQuestionIntent.filter("홍아람", List.of(untitled)).get(0).title()).isEmpty();
    }
}
