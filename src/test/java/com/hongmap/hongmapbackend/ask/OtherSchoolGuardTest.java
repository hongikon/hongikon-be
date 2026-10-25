package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 다른 대학 섞임 출구 검사(docs/ask-scope-policy.md 3장). 사례는 우리 원문(ask/sources.json)에 실제로 있는 언급에서 가져왔다. */
class OtherSchoolGuardTest {

    @Test
    void 근거_원문에_없는_다른_대학_이름은_막는다() {
        String ground = "### dorm-apply/start\n홍익대 기숙사는 dormitory.hongik.ac.kr 에서 온라인으로 지원해요.";
        assertThat(OtherSchoolGuard.ungrounded("연세대 기숙사는 1월에 모집해요.", ground)).contains("연세대");
        assertThat(OtherSchoolGuard.ungrounded("연세대학교도 비슷해요.", ground)).contains("연세대");
        assertThat(OtherSchoolGuard.ungrounded("Yonsei 기숙사", ground)).contains("Yonsei");
        // '로'·'교'가 붙어도 대학 이름으로 본다 — '연세대로 가는 길'이 빠져나가지 않게.
        assertThat(OtherSchoolGuard.ungrounded("연세대로 가려면", ground)).contains("연세대");
    }

    @Test
    void 우리_원문에_있는_다른_대학_언급은_통과한다() {
        String ground = "- 참여 대학: 경기대, 서강대, 숙명여대, 연세대, 이화여대, 홍익대, 국민대\n"
                + "- 상시 입주생 지원은 국민대, 숙명여대 학생만 해당함";
        assertThat(OtherSchoolGuard.ungrounded("행복기숙사 참여 대학은 서강대, 연세대, 이화여대 등이에요.", ground)).isEmpty();
        assertThat(OtherSchoolGuard.ungrounded("상시 입주생 지원은 국민대 학생만이에요.", ground)).isEmpty();
    }

    @Test
    void 대학이_아닌_낱말은_잡지_않는다() {
        String ground = "";
        assertThat(OtherSchoolGuard.ungrounded("7번 버스 서강초등학교 정류장에서 내려요.", ground)).isEmpty();
        assertThat(OtherSchoolGuard.ungrounded("전공필수는 미술원서강독이에요.", ground)).isEmpty();
        assertThat(OtherSchoolGuard.ungrounded("서울대입구역에서 갈아타요.", ground)).isEmpty();
        assertThat(OtherSchoolGuard.ungrounded("세종대왕 동상", ground)).isEmpty();
        assertThat(OtherSchoolGuard.ungrounded("교내 경기대회 일정", ground)).isEmpty();
        assertThat(OtherSchoolGuard.ungrounded("세종캠퍼스는 교무입시팀에 내요.", ground)).isEmpty();
        assertThat(OtherSchoolGuard.ungrounded("홍익대학교 학생증", ground)).isEmpty();
    }

    @Test
    void 실시간_전송은_이름_일부도_새어_나가지_않고_막힌_뒤로는_아무것도_보내지_않는다() {
        OtherSchoolGuard.Gate gate = new OtherSchoolGuard.Gate("홍익대 기숙사 안내");
        StringBuilder sent = new StringBuilder();
        for (String chunk : new String[]{"홍익대 기숙사는 1월에 모집해요. 참고로 연", "세", "대는 ", "2월이에요."}) {
            sent.append(gate.feed(chunk));
        }
        sent.append(gate.finish());
        assertThat(gate.blockedName()).isEqualTo("연세대");
        assertThat(sent.toString()).doesNotContain("연").doesNotContain("2월");
    }

    @Test
    void 막히지_않으면_붙잡아_둔_글자까지_모두_보낸다() {
        OtherSchoolGuard.Gate gate = new OtherSchoolGuard.Gate("");
        StringBuilder sent = new StringBuilder();
        for (String chunk : new String[]{"학생증은 신한 쏠 앱에서 ", "재발급을 신청해요."}) {
            sent.append(gate.feed(chunk));
        }
        sent.append(gate.finish());
        assertThat(gate.blockedName()).isNull();
        assertThat(sent.toString()).isEqualTo("학생증은 신한 쏠 앱에서 재발급을 신청해요.");
    }
}
