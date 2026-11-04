package com.hongmap.hongmapbackend.ask;

import java.util.Map;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
    void 학부_이름이면_두_전공을_함께_한_전공_이름이면_그_전공만() {
        for (String q : List.of("신소재화공 학회 뭐 있어?", "신소재 화공 학회", "신소재·화공시스템공학부 학회", "신소재화학공학 학회")) {
            AskBoards.Match match = AskBoards.find(q).orElseThrow();
            assertThat(match.group()).as(q).isTrue();
            assertThat(match.boards()).as(q).containsExactly("신소재공학전공", "화학공학전공");
            assertThat(match.strip(q)).as(q).doesNotContain("신소재").doesNotContain("화공").doesNotContain("화학공학").contains("학회");
        }
        AskBoards.Match mse = AskBoards.find("신소재 학회 뭐 있어?").orElseThrow();
        assertThat(mse.group()).isFalse();
        assertThat(mse.board()).isEqualTo("신소재공학전공");
        assertThat(mse.siblings()).containsExactly("화학공학전공");
        assertThat(AskBoards.find("화공 학회").orElseThrow().siblings()).containsExactly("신소재공학전공");
        assertThat(AskBoards.find("컴공 학회").orElseThrow().siblings()).isEmpty();
    }

    @Test
    void 학부로_물으면_두_전공_홈페이지_쪽을_한_전공이면_다른_전공_쪽을_뺀다() {
        AskRetriever.Index index = new AskRetriever.Index(List.of(
                new AskRetriever.Chunk("page:mse_club", "신소재공학전공 학회 바구니 스틸러스 블루위너스"),
                new AskRetriever.Chunk("page:chem_club", "화학공학과 학회 및 소모임 CH.E.S.T 신명화랑 Zero-base 신소재화공시스템 공학부 밴드"),
                new AskRetriever.Chunk("page:ce_club", "컴퓨터공학전공 학회 HI-ARC")),
                new float[][] {{1, 0}, {1, 0}, {1, 0}},
                Map.of("page:mse_club", new AskRetriever.Page("신소재 학생회", "신소재공학전공", "u1", "학회"),
                        "page:chem_club", new AskRetriever.Page("화공 소모임", "화학공학전공", "u2", "학회"),
                        "page:ce_club", new AskRetriever.Page("컴공 학회", "컴퓨터공학과", "u3", "학회")));
        AskRetriever retriever = new AskRetriever(new AskRetrieverTest.FakeEmbed(Map.of()), index, false);
        AskRouteService service = new AskRouteService(mock(AskCatalog.class), mock(AskLlmClient.class), mock(AskLiveData.class),
                retriever, mock(AskRuleGraph.class));

        // RAG 가 신소재 쪽만 골랐어도 학부로 물으면 화공 쪽까지.
        assertThat(service.departmentPages("신소재화공 학회 뭐 있어?", List.of("page:mse_club")))
                .containsExactlyInAnyOrder("page:mse_club", "page:chem_club");
        // 한 전공으로 물으면 같은 학부 다른 전공 쪽은 뺀다(다른 학부 쪽은 건드리지 않는다).
        assertThat(service.departmentPages("신소재 학회 뭐 있어?", List.of("page:chem_club", "page:mse_club")))
                .containsExactly("page:mse_club");
        assertThat(service.departmentPages("신소재 학회 뭐 있어?", List.of("page:chem_club"))).containsExactly("page:mse_club");
        assertThat(service.departmentPages("화공 학회", List.of())).containsExactly("page:chem_club");
        assertThat(service.departmentPages("컴공 학회", List.of("page:ce_club"))).containsExactly("page:ce_club");
        assertThat(service.departmentPages("휴학 신청", List.of("page:ce_club"))).containsExactly("page:ce_club");

        assertThat(AskRouteService.departmentLine("신소재화공 학회")).contains("[질문 속 학부]").contains("신소재공학전공·화학공학전공");
        assertThat(AskRouteService.departmentLine("신소재 학회")).contains("[질문 속 전공] 신소재공학전공").contains("화학공학전공 것은 섞지 않는다");
        assertThat(AskRouteService.departmentLine("컴공 학회")).isEmpty();
    }

    @Test
    void 질문의_내용_낱말() {
        assertThat(AskMissingTerms.words("동문장학금 언제까지야?", 3)).containsExactly("동문장학금");
    }
}
