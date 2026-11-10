package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 과목 DB·줄임말(11-10) — 실제 ask/courses.json·aliases.json 으로. */
class AskCoursesTest {

    private static final AskCourses.Data DATA = AskCourses.data();

    @Test
    void 데이터를_읽는다() {
        assertThat(DATA.courses()).hasSizeGreaterThan(2500);
        assertThat(DATA.curated()).contains("중동", "컴프", "선대수", "재전화", "협기", "화공정");
    }

    @Test
    void 줄임말은_조사가_붙어도_찾고_더_긴_낱말의_앞부분이면_아니다() {
        assertThat(AskCourses.aliasesIn("중동에서 신입 뽑아?", DATA)).containsKey("중동");
        assertThat(AskCourses.aliasLine("선대수 몇 학년 과목이야?")).contains("선형대수학");
        assertThat(AskCourses.aliasLine("운체 어려워?")).contains("운영체제");
        assertThat(AskCourses.aliasesIn("인공지능 수업 뭐 있어", DATA)).doesNotContainKey("인공");
        assertThat(AskCourses.expand("교환 가고 싶어")).contains("교환학생");
        assertThat(AskCourses.aliasLine("대물 몇 학점이야?")).contains("대학물리·대학물리실험");
        assertThat(AskCourses.search("C프", null, null, null, null, DATA)).contains("C-프로그래밍");
        assertThat(AskCourses.search("대수", null, null, null, null, DATA)).contains("대학수학");
    }

    @Test
    void 학과_학년이면_그_학년_과목_목록을_준다() {
        String out = AskCourses.search("", "컴퓨터공학과", 1, null, null, DATA);
        assertThat(out).startsWith(AskCourses.SOURCE_REF).contains("컴퓨터공학과 1학년").contains("대학수학");
        assertThat(out).doesNotContain("2학년");
    }

    @Test
    void 과목은_이름_줄임말_학수번호로_찾는다() {
        assertThat(AskCourses.search("선대", null, null, null, null, DATA)).contains("선형대수학");
        assertThat(AskCourses.search("012201", null, null, null, null, DATA)).contains("대학수학");
        assertThat(AskCourses.search("없는과목이름", null, null, null, null, DATA)).contains("찾지 못했어요");
        // 세종은 따로.
        assertThat(AskCourses.search("", "게임소프트웨어", 1, null, null, DATA)).contains("찾지 못했어요");
        assertThat(AskCourses.search("", "게임소프트웨어", 1, null, "세종", DATA)).contains("게임소프트웨어");
    }
}
