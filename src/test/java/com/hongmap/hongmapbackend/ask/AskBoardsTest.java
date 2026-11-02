package com.hongmap.hongmapbackend.ask;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 질문 속 학과·기관 이름 → 공지 게시판(10-25 학과 공지 장학금·행사를 답에 반영). */
class AskBoardsTest {

    @Test
    void 정식_이름과_줄임말을_게시판으로() {
        assertThat(AskBoards.find("컴퓨터공학과 동문 및 오승환 장학금")).get().extracting(AskBoards.Match::board).isEqualTo("컴퓨터공학과");
        assertThat(AskBoards.find("컴공 동문장학금 언제까지야")).get().extracting(AskBoards.Match::board).isEqualTo("컴퓨터공학과");
        assertThat(AskBoards.find("화공과 특강 있어?")).get().extracting(AskBoards.Match::board).isEqualTo("화학공학전공");
        assertThat(AskBoards.find("국어교육과 행사")).get().extracting(AskBoards.Match::board).isEqualTo("국어교육과");
        assertThat(AskBoards.find("교육학과 동문회 장학금")).get().extracting(AskBoards.Match::board).isEqualTo("교육학과");
        // 11-02: '산데공 학회'를 학과로 못 알아봐 학과 게시판·홈페이지를 찾지 않았다.
        assertThat(AskBoards.find("산데공 학회")).get().extracting(AskBoards.Match::board).isEqualTo("산업데이터공학과");
        assertThat(AskBoards.find("산업·데이터공학과 졸업요건")).get().extracting(AskBoards.Match::board).isEqualTo("산업데이터공학과");
        assertThat(AskBoards.hint("산데공 학회")).contains("'산데공'").contains("홍익대 산업데이터공학과");
        // 11-02 운영진 요청 줄임말.
        Map<String, String> shortNames = Map.ofEntries(
                Map.entry("캠자 전공 배정", "자율전공"), Map.entry("산디 졸업전시", "디자인학부"), Map.entry("시디 과제", "디자인학부"),
                Map.entry("금조디 실기실", "금속조형디자인과"), Map.entry("목조디 학회", "목조형가구학과"),
                Map.entry("패디 졸업작품", "섬유미술패션디자인과"), Map.entry("섬패디 공지", "섬유미술패션디자인과"),
                Map.entry("기시디 전공필수", "기계시스템디자인공학과"), Map.entry("법대 학생회", "법학부"),
                Map.entry("건환 졸업요건", "건설환경공학과"), Map.entry("국문과 행사", "국어국문학과"),
                Map.entry("실건 수업", "건축학부"), Map.entry("디예경 학회", "디자인예술경영학부"));
        shortNames.forEach((question, board) ->
                assertThat(AskBoards.find(question)).as(question).get().extracting(AskBoards.Match::board).isEqualTo(board));
        assertThat(AskBoards.hint("휴학 신청 기간")).isEmpty();
    }

    @Test
    void 학과가_아닌_낱말은_건너뛴다() {
        assertThat(AskBoards.find("교육과정 바뀌었어?")).isEmpty();
        assertThat(AskBoards.find("영어 회화 수업 있어?")).isEmpty();
        assertThat(AskBoards.find("휴학 신청 기간")).isEmpty();
    }

    @Test
    void 질문의_내용_낱말() {
        assertThat(AskMissingTerms.words("동문장학금 언제까지야?", 3)).containsExactly("동문장학금");
    }
}
