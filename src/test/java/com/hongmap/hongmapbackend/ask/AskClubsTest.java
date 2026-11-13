package com.hongmap.hongmapbackend.ask;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 중앙동아리(11-12) — 실제 ask/clubs.json(총학생회 중앙동아리 66개)으로. */
class AskClubsTest {

    private static final AskClubs.Data DATA = AskClubs.data();

    private static List<String> names(List<AskClubs.Club> clubs) {
        return clubs.stream().map(AskClubs.Club::name).toList();
    }

    @Test
    void 데이터를_읽는다() {
        assertThat(DATA.clubs()).hasSize(66);
        assertThat(DATA.fetchedAt()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(DATA.clubs()).allSatisfy(club -> {
            assertThat(AskClubs.CATEGORIES).contains(club.category());
            assertThat(club.room()).isNotBlank();
            assertThat(club.detailUrl()).startsWith(AskClubs.SOURCE_URL);
        });
    }

    @Test
    void 화면에_나갈_글에_이모지와_연락처가_없다() {
        for (AskClubs.Club club : DATA.clubs()) {
            String text = club.text() + club.recruiting() + club.schedule();
            assertThat(text.codePoints().filter(cp -> cp >= 0x1F000 && cp <= 0x1FAFF || cp >= 0x2600 && cp <= 0x27BF))
                    .as(club.name()).isEmpty();
            assertThat(text).doesNotContainPattern("[\\w.+-]+@[\\w-]+\\.[\\w.]+").doesNotContainPattern("01[016789]-?\\d{3,4}-?\\d{4}");
        }
    }

    @Test
    void 이름으로_찾으면_동방_모임_모집_링크를_자세히() {
        AskClubs.Result result = AskClubs.search("뚜라미 동방 어디야", null, DATA);
        assertThat(names(result.shown())).containsExactly("뚜라미");
        assertThat(result.text()).startsWith(AskClubs.SOURCE_REF)
                .contains("B314-2호(제2기숙사 지하층 314-2호)")
                .contains("정기 모임: 자율참석")
                .contains("모집 안내(동아리가 적은 문구): 매학기 초")
                .contains("club:22");
        assertThat(names(AskClubs.search("뚜라미 언제 모여", null, DATA).shown())).containsExactly("뚜라미");
        assertThat(names(AskClubs.named("뚜라미동방 어디", DATA))).containsExactly("뚜라미");
    }

    @Test
    void 괄호_이름은_어느_쪽으로_물어도_찾는다() {
        assertThat(names(AskClubs.named("HIUC 인스타", DATA))).containsExactly("HIUC(합창반)");
        assertThat(names(AskClubs.named("합창반은 언제 모여?", DATA))).containsExactly("HIUC(합창반)");
        assertThat(names(AskClubs.named("hiuc 동방", DATA))).containsExactly("HIUC(합창반)");
        assertThat(AskClubs.search("HIUC 인스타", null, DATA).text()).contains("인스타그램: https://www.instagram.com/").contains("[참조 club:9:link0]");
        assertThat(names(AskClubs.named("네페르 동방", DATA))).containsExactly("고전기타반 네페르");
        assertThat(names(AskClubs.named("위너스 어디서 모여", DATA))).containsExactly("Winners(야구반)");
        // 더 긴 낱말의 앞부분이면 아니다('HIUC'는 'HIU'가 아니고, '원플러스'는 '원플'이 아니다).
        assertThat(names(AskClubs.named("원플러스원 행사", DATA))).isEmpty();
        assertThat(names(AskClubs.named("홍익대학교 학생회 어디", DATA))).isEmpty();
    }

    @Test
    void 분야_낱말은_소개_활동에서_찾는다() {
        assertThat(names(AskClubs.search("밴드 동아리 뭐 있어", null, DATA).shown())).isEmpty(); // 후보가 많아 한 줄씩
        assertThat(AskClubs.search("밴드 동아리 뭐 있어", null, DATA).text()).contains("블랙테트라").contains("뚜라미");
        assertThat(names(AskClubs.search("축구 동아리", null, DATA).shown())).contains("일레븐킥스");
        assertThat(names(AskClubs.search("춤 동아리", null, DATA).shown())).containsExactly("비츠플로우(Beatzflow)");
        assertThat(AskClubs.search("사진 동아리 있어?", null, DATA).text()).contains("모래알 사진반");
        String volunteer = AskClubs.search("봉사 동아리", null, DATA).text();
        assertThat(volunteer).contains("멍냥부리", "한울", "그루터기", "영원한 미소", "호우회", "KUSA");
        // 분과로 좁힌다.
        assertThat(AskClubs.search("봉사", "사회", DATA).text()).doesNotContain("공연분과");
    }

    @Test
    void 빈_검색어면_분과별_전체_목록_분과를_주면_그_분과만() {
        String all = AskClubs.search("", null, DATA).text();
        for (String category : AskClubs.CATEGORIES) {
            assertThat(all).contains("[" + category);
        }
        assertThat(all.lines().filter(l -> l.startsWith("- ")).count()).isEqualTo(66);
        assertThat(AskClubs.search("중동 뭐 있어", null, DATA).text().lines().filter(l -> l.startsWith("- ")).count()).isEqualTo(66);
        String sports = AskClubs.search("스포츠 동아리 목록", null, DATA).text();
        assertThat(sports).contains("[스포츠분과 12개]").doesNotContain("[공연분과");
        assertThat(AskClubs.search("", "종교분과", DATA).text()).contains("[종교분과 4개]");
    }

    @Test
    void 없는_동아리는_찾지_못했다고_한다() {
        AskClubs.Result result = AskClubs.search("예수전도단 동방", null, DATA);
        assertThat(result.found()).isFalse();
        assertThat(result.text()).contains("찾지 못했어요").contains("가동아리");
    }

    @Test
    void 동방_호실은_건물까지_풀어_쓴다() {
        assertThat(AskClubs.roomLabel("G323-6호")).isEqualTo("G323-6호(학생회관 G동 3층 323-6호)");
        assertThat(AskClubs.roomLabel("D413호")).isEqualTo("D413호(인문사회관 D동 4층 413호)");
        assertThat(AskClubs.roomLabel("2기숙사 지하3층")).isEqualTo("2기숙사 지하3층");
        assertThat(AskClubs.building("G210호")).isEqualTo("학생회관");
        assertThat(AskClubs.building("B307-1호")).isEqualTo("제2기숙사");
    }

    @Test
    void 질문_속_동아리를_알리는_한_줄() {
        assertThat(AskClubs.hint("뚜라미 동방 어디야")).contains("뚜라미(공연분과)").contains("search_clubs");
        assertThat(AskClubs.hint("휴학 신청 어떻게 해")).isEmpty();
        // 줄임말 '중동'은 그대로 과목 DB 줄임말로 풀린다(동아리 이름으로 잡지 않는다).
        assertThat(AskCourses.aliasesIn("중동 뭐 있어", AskCourses.data())).containsKey("중동");
        assertThat(AskClubs.hint("중동 뭐 있어")).isEmpty();
    }
}
