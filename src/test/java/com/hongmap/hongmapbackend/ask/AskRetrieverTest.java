package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RAG 검색(10-28): 임베딩 + BM25 순위 합치기, 안내 묶기, 꺼짐 조건, 임베딩 서버 실패. */
class AskRetrieverTest {

    /** 글자에 따라 정해진 벡터를 돌려주는 가짜 임베딩 서버(2차원). */
    static class FakeEmbed extends AskEmbedClient {
        final Map<String, float[]> answers;
        boolean fail;

        FakeEmbed(Map<String, float[]> answers) {
            super(JsonMapper.builder().build(), "http://127.0.0.1:1", "test-key-1234567890", 100);
            this.answers = answers;
        }

        @Override
        public float[] embed(String text) {
            if (fail) {
                throw new AskUnavailableException("꺼짐");
            }
            return answers.getOrDefault(text, new float[] {0, 0});
        }
    }

    private static AskRetriever.Index index() {
        return new AskRetriever.Index(List.of(
                new AskRetriever.Chunk("leave/start", "휴학 신청은 클래스넷에서 해요"),
                new AskRetriever.Chunk("leave/start", "휴학 연장은 기간 안에"),
                new AskRetriever.Chunk("curriculum-rules/start", "필수과목이 폐지되었을 경우 경과조치가 없으면 취득하지 않아도 된다"),
                new AskRetriever.Chunk("grad-ce/start", "컴퓨터공학과 전공필수 자료구조및프로그래밍")),
                new float[][] {{1, 0}, {1, 0}, {0, 1}, {0.6f, 0.8f}});
    }

    @Test
    void 뜻이_가까운_문단을_낱말이_안_겹쳐도_찾고_안내로_묶는다() {
        FakeEmbed embed = new FakeEmbed(Map.of("전필 안 들어도 졸업돼?", new float[] {0, 1}));
        AskRetriever retriever = new AskRetriever(embed, index(), true);
        assertThat(retriever.enabled()).isTrue();
        List<String> nodes = retriever.nodes("전필 안 들어도 졸업돼?", 3);
        assertThat(nodes.get(0)).isEqualTo("curriculum-rules/start");
        assertThat(nodes).doesNotHaveDuplicates();
    }

    @Test
    void 낱말이_겹치면_BM25_가_순위를_끌어올린다() {
        FakeEmbed embed = new FakeEmbed(Map.of("컴퓨터공학과 전공필수", new float[] {0.7f, 0.7f}));
        AskRetriever retriever = new AskRetriever(embed, index(), true);
        assertThat(retriever.nodes("컴퓨터공학과 전공필수", 1)).containsExactly("grad-ce/start");
    }

    @Test
    void 이웃_안내는_안내_평균_벡터로_뜻이_가까운_것과_질문에_가까운_순서를_고른다() {
        AskRetriever retriever = new AskRetriever(new FakeEmbed(Map.of()), index(), true);
        // curriculum-rules (0,1) 과 grad-ce (0.6,0.8) 는 0.8, leave (1,0) 과 grad-ce 는 0.6 — 0.75 이상만 이웃.
        assertThat(retriever.similarGuides("curriculum-rules/start")).containsExactly("grad-ce/start");
        assertThat(retriever.similarGuides("leave/start")).isEmpty();
        assertThat(retriever.similarGuides("없는/안내")).isEmpty();
        assertThat(retriever.closestToQuery(List.of("leave/start", "grad-ce/start", "없는/안내"), new float[] {0, 1}, 1))
                .containsExactly("grad-ce/start");
        assertThat(retriever.closestToQuery(List.of("leave/start"), null, 2)).isEmpty();
    }

    @Test
    void RAG_가_꺼져_있으면_이웃_안내도_끈다() {
        AskRetriever off = new AskRetriever(new FakeEmbed(Map.of()), index(), false);
        off.neighborLimit = 2;
        assertThat(off.neighborLimit()).isZero();
        AskRetriever on = new AskRetriever(new FakeEmbed(Map.of()), index(), true);
        on.neighborLimit = 2;
        assertThat(on.neighborLimit()).isEqualTo(2);
    }

    @Test
    void 색인이나_키가_없으면_꺼지고_빈_후보() {
        assertThat(new AskRetriever(new FakeEmbed(Map.of()), null, true).enabled()).isFalse();
        assertThat(new AskRetriever(new FakeEmbed(Map.of()), index(), false).nodes("휴학", 3)).isEmpty();
    }

    @Test
    void 검색은_임베딩_서버_호출과_색인_검색_시간을_따로_잰다() {
        FakeEmbed slow = new FakeEmbed(Map.of()) {
            @Override
            public float[] embed(String text) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new float[] {1, 0};
            }
        };
        AskRetriever.Found found = new AskRetriever(slow, index(), true).search("휴학", 3, 2);
        assertThat(found.nodes()).isNotEmpty();
        assertThat(found.embedMs()).isGreaterThanOrEqualTo(25);
        assertThat(found.searchMs()).isGreaterThanOrEqualTo(0).isLessThan(found.embedMs());

        AskRetriever.Found off = new AskRetriever(slow, index(), false).search("휴학", 3, 2);
        assertThat(off.nodes()).isEmpty();
        assertThat(off.embedMs()).isZero();
    }

    @Test
    void 임베딩_서버가_실패하면_낱말로_대신하지_않고_실패한다() {
        FakeEmbed embed = new FakeEmbed(Map.of());
        embed.fail = true;
        AskRetriever retriever = new AskRetriever(embed, index(), true);
        assertThatThrownBy(() -> retriever.nodes("휴학", 3)).isInstanceOf(AskUnavailableException.class);
    }

    @Test
    void 홈페이지_쪽은_안내와_따로_앞쪽_문단에서만_고른다() {
        AskRetriever.Index index = new AskRetriever.Index(List.of(
                new AskRetriever.Chunk("page:depthome_mse_0403", "신소재공학전공 홈페이지 · 졸업요건 실험과목 2과목"),
                new AskRetriever.Chunk("grad-mse/start", "신소재공학전공 졸업요건")),
                new float[][] {{1, 0}, {0.9f, 0.1f}},
                Map.of("page:depthome_mse_0403", new AskRetriever.Page("신소재공학전공 홈페이지 · 졸업요건", "신소재공학전공",
                        "https://mse.hongik.ac.kr/mse/0403.do", "실험과목 2과목")));
        AskRetriever retriever = new AskRetriever(new FakeEmbed(Map.of("신소재 실험과목", new float[] {1, 0})), index, true);
        AskRetriever.Found found = retriever.search("신소재 실험과목", 3, 2);
        assertThat(found.nodes()).containsExactly("grad-mse/start");
        assertThat(found.pages()).containsExactly("page:depthome_mse_0403");
        assertThat(retriever.page("page:depthome_mse_0403").url()).endsWith("0403.do");
        assertThat(retriever.search("신소재 실험과목", 3, 0).pages()).isEmpty();
    }

    @Test
    void RAG_가_꺼져도_그_학과_홈페이지_쪽만_낱말로_찾는다() {
        AskRetriever.Index index = new AskRetriever.Index(List.of(
                new AskRetriever.Chunk("page:depthome_ce_0103", "컴퓨터공학전공 학회 소개 GDSC Hongik 개발 커뮤니티 HI-ARC 알고리즘"),
                new AskRetriever.Chunk("page:depthome_ce_0201", "컴퓨터공학과 졸업요건 130학점"),
                new AskRetriever.Chunk("page:depthome_ee_0104", "전자전기공학부 학회 소개")),
                new float[][] {{1, 0}, {1, 0}, {1, 0}},
                Map.of("page:depthome_ce_0103", new AskRetriever.Page("학회 소개", "컴퓨터공학과", "https://wwwce.hongik.ac.kr/wwwce/0103.do", "학회"),
                        "page:depthome_ce_0201", new AskRetriever.Page("졸업요건", "컴퓨터공학과", "https://wwwce.hongik.ac.kr/wwwce/0201.do", "졸업"),
                        "page:depthome_ee_0104", new AskRetriever.Page("학회 소개", "전자전기공학부", "https://ee.hongik.ac.kr/ee/0104.do", "학회")));
        AskRetriever off = new AskRetriever(new FakeEmbed(Map.of()), index, false);
        assertThat(off.enabled()).isFalse();
        assertThat(off.deptPages("컴퓨터공학과", "학회", 2)).containsExactly("page:depthome_ce_0103");
        assertThat(off.deptPages("컴퓨터공학과", "아무말", 2)).isEmpty();
        assertThat(off.deptPages("없는학과", "학회", 2)).isEmpty();
    }

    @Test
    void 반정밀도_변환과_2글자_자르기() {
        assertThat(AskRetriever.halfToFloat((short) 0x3c00)).isEqualTo(1.0f);
        assertThat(AskRetriever.halfToFloat((short) 0xb800)).isEqualTo(-0.5f);
        assertThat(AskRetriever.bigrams("전공 필수")).containsExactly("전공", "공필", "필수");
    }
}
