package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import com.hongmap.hongmapbackend.ask.dto.AskRouteRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AskPrivacyTest {

    @Test
    void 학번_휴대전화_이메일_주민번호_긴_숫자열을_가린다() {
        assertThat(AskPrivacy.mask("B612345 학생이고 010-1234-5678, 01012345678, me@g.hongik.ac.kr, 990101-1234567, 계좌 110123456789"))
                .isEqualTo("[학번] 학생이고 [전화번호], [전화번호], [이메일], [주민번호], 계좌 [번호]");
    }

    @Test
    void 학교_번호와_호실_학기_날짜는_그대로_둔다() {
        String text = "학사지원팀 02-320-1037, T0101·T501 강의실, 2026학년도 2학기 9.1(화)";
        assertThat(AskPrivacy.mask(text)).isEqualTo(text);
        assertThat(AskPrivacy.mask((String) null)).isNull();
    }

    @Test
    void 보내기_전에_질문과_직전_대화를_가리고_안내_id는_그대로_둔다() {
        AskRouteRequest request = new AskRouteRequest("B612345 인데 휴학 돼요?", "leave/start",
                List.of(new AskRouteRequest.Turn("내 번호 010-1234-5678", "contact/start", "me@g.hongik.ac.kr 로 보내요")), "conv-12345678");
        AskRouteRequest masked = AskPrivacy.mask(request);
        assertThat(masked.question()).isEqualTo("[학번] 인데 휴학 돼요?");
        assertThat(masked.hint()).isEqualTo("leave/start");
        assertThat(masked.conversationId()).isEqualTo("conv-12345678");
        assertThat(masked.history()).containsExactly(new AskRouteRequest.Turn("내 번호 [전화번호]", "contact/start", "[이메일] 로 보내요"));
        assertThat(AskPrivacy.mask(new AskRouteRequest("휴학 언제야?", null, null)).history()).isNull();
    }
}
