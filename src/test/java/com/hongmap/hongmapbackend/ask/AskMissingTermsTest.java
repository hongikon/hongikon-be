package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 고른 안내 원문에 없는 질문 낱말(10-25 '컴퓨터공학과 동문 및 오승환 장학금'이 교내장학금 안내로만 답하던 것). */
class AskMissingTermsTest {

    private static final String SCHOLARSHIP = "교내장학금 대부분(7종)은 따로 신청하지 않아요. 홍익인간 · 자주 · 창조 장학금 · 동문 선배";

    @Test
    void 원문에_없는_이름과_학과를_뽑는다() {
        assertThat(AskMissingTerms.find("컴퓨터공학과 동문 및 오승환 장학금", SCHOLARSHIP))
                .containsExactly("컴퓨터공학", "오승환");
    }

    @Test
    void 물음_말투와_원문에_있는_낱말은_뺀다() {
        assertThat(AskMissingTerms.find("교내장학금 신청 어떻게 해요?", SCHOLARSHIP)).isEmpty();
        assertThat(AskMissingTerms.find("홍익인간 장학금 알려줘", SCHOLARSHIP)).isEmpty();
    }

    @Test
    void 조사를_지우고_공백은_무시한다() {
        assertThat(AskMissingTerms.stem("오승환은")).isEqualTo("오승환");
        assertThat(AskMissingTerms.stem("학과")).isEqualTo("학과");
        assertThat(AskMissingTerms.stem("결석하면")).isEqualTo("결석");
        assertThat(AskMissingTerms.stem("공결돼")).isEqualTo("공결");
        assertThat(AskMissingTerms.find("창조장학금은", SCHOLARSHIP)).isEmpty();
    }

    @Test
    void 최대_세_개() {
        assertThat(AskMissingTerms.find("가나다 라마바 사아자 차카타", "")).hasSize(AskMissingTerms.MAX_TERMS);
    }
}
