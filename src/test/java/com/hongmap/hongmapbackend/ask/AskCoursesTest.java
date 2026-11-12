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

    /** 11-10 실제 모델이 '컴공 1학년 과목'을 query 에 통째로 넣었다 — 학과·학년을 칸으로 옮기고, 이름으로 못 찾으면 학과 목록으로. */
    @Test
    void 검색어에_섞인_학과_학년을_칸으로_옮긴다() {
        String out = AskCourses.search("컴공 1학년 과목", null, null, null, null, DATA);
        assertThat(out).contains("컴퓨터공학과 1학년").contains("대학수학").contains("C-프로그래밍").doesNotContain("2학년");
        assertThat(AskCourses.search("1학년 뭐 들어", "컴공", null, null, null, DATA)).contains("컴퓨터공학과 1학년");
    }

    /** 11-12: 이수구분이 합쳐진 칸 범위로 채워져 거의 모든 행에 있다(예전 3047행 중 2663행이 비어 있었다). */
    @Test
    void 이수구분이_거의_모든_행에_있다() {
        long empty = DATA.courses().stream().filter(c -> c.category() == null).count();
        assertThat(empty).isLessThan(DATA.courses().size() / 50);
    }

    @Test
    void 이수구분_필터_전필_줄임말도_같다() {
        String full = AskCourses.search("", "컴퓨터공학과", 3, null, "전공필수", null, AskCourses.Mode.LIST, DATA);
        assertThat(full).contains("운영체제").contains("컴퓨터구조").doesNotContain("컴퓨터네트워크").doesNotContain("의사소통");
        assertThat(AskCourses.search("", "컴공", 3, null, "전필", null, AskCourses.Mode.LIST, DATA)).isEqualTo(full);
        // query 에 섞여 온 '전공필수'·'전필'은 지우지 않고 필터로 옮긴다.
        assertThat(AskCourses.search("컴공 3학년 전필", null, null, null, null, DATA)).isEqualTo(full);
        assertThat(AskCourses.search("컴공 3학년 전공필수 과목", null, null, null, null, DATA)).isEqualTo(full);
        assertThat(AskCourses.normalizeCategory("전선")).isEqualTo("전공선택");
        assertThat(AskCourses.normalizeCategory("MSC")).isEqualTo("MSC");
        String elective = AskCourses.search("화공 2학년 2학기 전공선택 과목", null, null, null, null, DATA);
        assertThat(elective).contains("화학공학전공 2학년 2학기").contains("전공선택").doesNotContain("전공필수").doesNotContain("1학기");
    }

    @Test
    void 학과별_집계는_모든_행을_센다() {
        String out = AskCourses.search("", null, 4, 1, "전공필수", null, AskCourses.Mode.COUNT_BY_DEPT, DATA);
        assertThat(out).startsWith(AskCourses.SOURCE_REF).contains("맞는 학과").contains("컴퓨터공학과: 1과목 · 3학점").contains("소프트웨어공학");
        long depts = out.lines().filter(l -> l.startsWith("- ")).count();
        assertThat(depts).isGreaterThanOrEqualTo(5);
        // 학과·이름 없이 이수구분·학년만 오면 list 를 청해도 집계로 답한다(목록 45줄 상한에 잘리지 않게).
        assertThat(AskCourses.search("4학년 1학기에 전공필수 있는 학과 찾아줘", null, null, null, null, DATA)).isEqualTo(out);
        // 학과 하나의 학점 합.
        String cs = AskCourses.search("", "컴공", 3, null, "전필", null, AskCourses.Mode.COUNT_BY_DEPT, DATA);
        assertThat(cs).contains("컴퓨터공학과: 4과목 · 12학점");
        assertThat(cs.lines().filter(l -> l.startsWith("- ")).count()).isEqualTo(1);
        // '학점 합'처럼 더하는 질문이면 list 로 불러도 집계(모델이 직접 더하지 않게).
        assertThat(AskCourses.search("컴공 3학년 전공필수 학점 합", null, null, null, null, DATA)).isEqualTo(cs);
        // 과목 하나의 '몇 학점'은 그대로 목록.
        assertThat(AskCourses.search("컴프 몇 학점이야", null, null, null, null, DATA)).doesNotContain("맞는 학과");
        // 가장 많은 학과 — 과목 수 많은 순 첫 줄.
        String most = AskCourses.search("전필이 가장 많은 학과", null, null, null, null, DATA);
        assertThat(most).contains("맞는 학과");
        assertThat(AskCourses.search("", null, null, null, "전필", "세종", AskCourses.Mode.COUNT_BY_DEPT, DATA)).contains("세종");
    }
}
