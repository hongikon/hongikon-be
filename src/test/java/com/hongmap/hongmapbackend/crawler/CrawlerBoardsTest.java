package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;
import com.hongmap.hongmapbackend.crawler.config.ParserType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 크롤러 게시판 목록이 앱(hongikon-fe src/constants/news.ts TREE_DATA)의 구독 가능한 게시판 49개를 모두 덮는지.
 * 앱에서 고를 수 있는데 서버가 모르는 id면 구독이 400으로 막히고 소식 탭이 영영 비어 있다.
 */
class CrawlerBoardsTest {

    /** TREE_DATA → SUBSCRIBABLE_ITEMS 리프 id 49개(2026.10 기준). 앱 목록이 바뀌면 같이 고친다. */
    private static final List<String> APP_SOURCE_IDS = List.of(
            "교수학습지원", "대학혁신지원사업", "장학", "학사", "학생상담", "학생활동",
            "건축학부", "도시학과",
            "경영학부",
            "경제학부",
            "건설환경공학과", "기계시스템디자인공학과", "기초과학과", "산업데이터공학과",
            "신소재공학전공", "화학공학전공", "전자전기공학부", "컴퓨터공학과",
            "뮤지컬전공", "실용음악전공",
            "디자인예술경영학부", "디자인경영전공", "예술경영전공",
            "국어국문학과", "독어독문학과", "불어불문학과", "영어영문학과",
            "금속조형디자인과", "도예유리과", "동양화과", "디자인학부", "목조형가구학과", "섬유미술패션디자인과",
            "예술학과", "자율전공", "조소과", "판화과", "회화과",
            "바이오헬스융합학부",
            "법학부",
            "교육학과", "국어교육과", "수학교육과", "역사교육과", "영어교육과",
            "데이터사이언스전공", "디자인엔지니어링전공", "사물인터넷공학전공", "지능로봇공학전공"
    );

    private static final Set<String> BOARD_SOURCE_IDS = CrawlerBoards.ALL.stream()
            .map(BoardConfig::sourceId)
            .collect(Collectors.toSet());

    @Test
    void 앱의_구독_게시판_49개가_모두_크롤러_게시판이거나_상위_게시판_별칭이다() {
        assertThat(APP_SOURCE_IDS).hasSize(49).doesNotHaveDuplicates();
        assertThat(CrawlerBoards.KNOWN_SOURCE_IDS).containsAll(APP_SOURCE_IDS);
    }

    @Test
    void 별칭은_실제로_수집하는_게시판을_가리키고_자신은_게시판이_아니다() {
        CrawlerBoards.SOURCE_ALIASES.forEach((alias, parent) -> {
            assertThat(BOARD_SOURCE_IDS).as(alias + " → " + parent).contains(parent);
            assertThat(BOARD_SOURCE_IDS).as(alias).doesNotContain(alias);
        });
    }

    @Test
    void 이번에_빠져_있던_게시판이_설정돼_있다() {
        assertThat(board("science"))
                .extracting(BoardConfig::sourceId, BoardConfig::listUrl, BoardConfig::tableSummary, BoardConfig::parser)
                .containsExactly("기초과학과", "https://science.hongik.ac.kr/science/0401.do", "학과공지사항", ParserType.HONGIK);
        assertThat(board("fm"))
                .extracting(BoardConfig::sourceId, BoardConfig::listUrl, BoardConfig::parser)
                .containsExactly("자율전공", "https://fm.hongik.ac.kr/fm/0401.do", ParserType.HONGIK);
        assertThat(board("smpd"))
                .extracting(BoardConfig::sourceId, BoardConfig::listUrl, BoardConfig::parser)
                .containsExactly("디자인엔지니어링전공", "https://smpd.hongik.ac.kr/smpd/0401.do", ParserType.HONGIK);
        assertThat(board("biohealth"))
                .extracting(BoardConfig::sourceId, BoardConfig::listUrl, BoardConfig::parser)
                .containsExactly("바이오헬스융합학부", "https://biohealth.hongik.ac.kr/22", ParserType.IMWEB);
    }

    @Test
    void 조회할_때는_별칭을_상위_게시판_id로_바꾸고_중복을_없앤다() {
        assertThat(CrawlerBoards.resolveStoredSourceIds(List.of("데이터사이언스전공", "산업데이터공학과", "학사")))
                .containsExactly("산업데이터공학과", "학사");
        assertThat(CrawlerBoards.resolveStoredSourceIds(List.of("디자인경영전공", "예술경영전공")))
                .containsExactly("디자인예술경영학부");
        assertThat(CrawlerBoards.resolveStoredSourceIds(List.of())).isEmpty();
    }

    @Test
    void 푸시할_때는_상위_게시판_구독자와_별칭_구독자를_함께_찾는다() {
        assertThat(CrawlerBoards.subscriberSourceIds("디자인예술경영학부"))
                .containsExactlyInAnyOrder("디자인예술경영학부", "디자인경영전공", "예술경영전공");
        assertThat(CrawlerBoards.subscriberSourceIds("전자전기공학부"))
                .containsExactlyInAnyOrder("전자전기공학부", "사물인터넷공학전공");
        assertThat(CrawlerBoards.subscriberSourceIds("컴퓨터공학과")).containsExactly("컴퓨터공학과");
    }

    private BoardConfig board(String boardKey) {
        return CrawlerBoards.ALL.stream()
                .filter(b -> b.boardKey().equals(boardKey))
                .findFirst()
                .orElseThrow();
    }
}
