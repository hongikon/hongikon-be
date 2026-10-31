package com.hongmap.hongmapbackend.ask;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AskSituationTest {

    @Test
    void 질문에서_상황_칸을_읽는다() {
        assertThat(AskSituation.from("4학년인데 재수강 선삭제 하고 싶어")).containsEntry("학년", 4);
        assertThat(AskSituation.from("신입생인데 휴학 돼?")).containsEntry("학년", 1);
        assertThat(AskSituation.from("장학생인데 휴학하면?")).containsEntry("장학생", true);
        assertThat(AskSituation.from("분할납부 중에 입대해")).containsEntry("분할납부", true).containsEntry("군복무", true);
        assertThat(AskSituation.from("졸업유예 중인데 재수강 돼?")).containsEntry("졸업유예", true);
        assertThat(AskSituation.from("지금 휴학 중인데 전과 가능해?")).containsEntry("휴학중", true);
        assertThat(AskSituation.from("3학기 마쳤는데 전과")).containsEntry("이수학기", 3);
        assertThat(AskSituation.from("휴학 신청 언제야?")).isEmpty();
    }

    @Test
    void 조건이_모두_맞을_때만_통과() {
        Map<String, Object> senior = AskSituation.from("4학년 장학생");
        assertThat(AskSituation.satisfies(null, senior)).isTrue();
        assertThat(AskSituation.satisfies(Map.of("학년", 4), senior)).isTrue();
        assertThat(AskSituation.satisfies(Map.of("학년", 1), senior)).isFalse();
        assertThat(AskSituation.satisfies(Map.of("장학생", true, "학년", 4), senior)).isTrue();
        assertThat(AskSituation.satisfies(Map.of("분할납부", true), senior)).isFalse();
        assertThat(AskSituation.satisfies(Map.of("장학생", true), Map.of())).isFalse();
    }
}
