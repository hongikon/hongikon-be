package com.hongmap.hongmapbackend.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsCategoryClassifierTest {

    @Test
    void 장학_게시판_글은_제목과_상관없이_장학() {
        assertThat(NewsCategoryClassifier.classify("2026년 든든 학업지원금 공고문", null, "장학")).isEqualTo("장학");
    }

    @Test
    void 본문에_장학이_있으면_장학() {
        assertThat(NewsCategoryClassifier.classify("2026년 든든 학업지원금 공고문", "교내 장학금 신청 안내", "학사"))
                .isEqualTo("장학");
    }

    @Test
    void 공고만_있는_제목은_더이상_취업이_아니다() {
        assertThat(NewsCategoryClassifier.classify("2026년 든든 학업지원금 공고문", "지원 대상 안내", "학사"))
                .isEqualTo("공지");
    }

    @Test
    void 채용_공고는_여전히_취업() {
        assertThat(NewsCategoryClassifier.classify("[삼립] 2026 하반기 신입사원 채용 공고", null, "화학공학전공"))
                .isEqualTo("취업");
    }

    @Test
    void 다른_규칙은_그대로() {
        assertThat(NewsCategoryClassifier.classify("2학기 수강신청 안내", null, "학사")).isEqualTo("수강");
        assertThat(NewsCategoryClassifier.classify("본관 정전 안내", "", "학사")).isEqualTo("시설");
    }
}
