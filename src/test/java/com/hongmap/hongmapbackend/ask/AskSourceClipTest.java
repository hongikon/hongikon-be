package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 긴 원문 자르기(11-21) — 11-14 기록: 자매대학 목록 18,000자를 8,000자에서 잘라 뒤쪽 NTU 를 '목록에 없다'고 답했다. */
class AskSourceClipTest {

    private static String partners() {
        StringBuilder out = new StringBuilder("교류협정 대학 목록이에요.\n");
        for (int i = 0; i < 600; i++) {
            out.append("국가").append(i).append(" · Partner University ").append(i).append(" · 교환\n");
        }
        return out.append("싱가포르 · Nanyang Technological University · 교환\n").toString();
    }

    @Test
    void 짧은_원문은_그대로() {
        assertThat(AskLlmClient.clip("짧은 원문", "[이번 질문]\n아무거나")).isEqualTo("짧은 원문");
    }

    @Test
    void 긴_원문은_앞머리와_질문_낱말이_든_줄을_남긴다() {
        String source = partners();
        assertThat(source.length()).isGreaterThan(AskLlmClient.SOURCE_MAX_CHARS);
        String clipped = AskLlmClient.clip(source, "[오늘] …\n[이번 질문]\n우리학교에서 Nanyang Technological University랑 자매대학인가\n[공지] 무관한 글");

        assertThat(clipped.length()).isLessThanOrEqualTo(AskLlmClient.SOURCE_MAX_CHARS + 100);
        assertThat(clipped).startsWith("교류협정 대학 목록이에요.").contains("Nanyang Technological University").contains("…")
                .endsWith("나머지는 생략)");
    }

    @Test
    void 맞는_줄이_없으면_예전처럼_앞에서부터() {
        String clipped = AskLlmClient.clip(partners(), "[이번 질문]\n없는낱말");
        assertThat(clipped).contains("Partner University 0 ").doesNotContain("…").endsWith("(이하 생략)");
    }

    @Test
    void 질문_낱말은_이번_질문과_직전_학생_질문에서만_조사를_떼어() {
        assertThat(AskLlmClient.focusWords("[직전 대화]\n학생: 교환학생 가고 싶어\n홍익온: 답\n\n[이번 질문]\n자매대학인가"))
                .contains("교환학생", "자매대학인가", "자매대학").doesNotContain("답", "홍익온");
    }
}
