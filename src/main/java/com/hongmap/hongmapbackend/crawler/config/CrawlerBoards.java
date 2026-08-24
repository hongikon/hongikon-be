package com.hongmap.hongmapbackend.crawler.config;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 크롤링 대상 게시판 목록. hongmap(프론트) scripts/crawler/config.mjs 를 그대로 포팅했다.
 *
 * parser=ARCH/IMWEB인 게시판은 실제 파싱 로직(ArchBoardParser/ImwebBoardParser)이 아직 TODO라
 * 지금 이 목록으로 바로 크롤링을 돌리면 해당 게시판은 로그만 남기고 스킵된다 — 그래도 목록 자체는
 * 프론트 크롤러와 어긋나지 않게 미리 맞춰뒀다.
 */
public final class CrawlerBoards {

    private static final int DEPARTMENT_MAX_ITEMS = 100;
    private static final int UNIVERSITY_MAX_ITEMS = 100;

    /** 분류는 세종캠퍼스가 아닌데 제목에 "[세종캠퍼스]"가 섞여 들어오는 대학공지를 한 번 더 거른다. */
    private static final Pattern SEJONG_TITLE = Pattern.compile("세종캠퍼스");

    private static final String UNIVERSITY_NOTICE_URL = "https://www.hongik.ac.kr/kr/education/notice-undergrad.do";
    private static final String UNIVERSITY_TABLE = "교육-대학공지";

    public static final List<BoardConfig> DEPARTMENT_BOARDS = List.of(
            hongik("ce", "컴퓨터공학과", "컴퓨터공학과", "https://wwwce.hongik.ac.kr/wwwce/0401.do", "학과 공지사항"),
            hongik("ce-job", "컴퓨터공학과", "컴퓨터공학과 취업·인턴", "https://wwwce.hongik.ac.kr/wwwce/0402.do", "취업인턴"),
            hongik("ee", "전자전기공학부", "전자전기공학부", "https://ee.hongik.ac.kr/ee/0501.do", "학부 게시판"),
            hongik("mse", "신소재공학전공", "신소재공학전공", "https://mse.hongik.ac.kr/mse/0501.do", "학과게시판"),
            hongik("chem", "화학공학전공", "화학공학전공", "https://chemeng.hongik.ac.kr/chemeng/sub/0401.do", "공지사항"),
            hongik("chem-job", "화학공학전공", "화학공학전공 인턴·취업", "https://chemeng.hongik.ac.kr/chemeng/sub/0402.do", "인턴 / 취업"),
            hongik("ie", "산업데이터공학과", "산업·데이터공학과", "https://ie.hongik.ac.kr/ie/0401.do", "학과 공지사항"),
            hongik("ie-job", "산업데이터공학과", "산업·데이터공학과 채용", "https://ie.hongik.ac.kr/ie/0402.do", "채용 공지사항"),
            hongik("me", "기계시스템디자인공학과", "기계·시스템디자인공학과", "https://me.hongik.ac.kr/me/0701.do", "공지사항"),
            hongik("me-doc", "기계시스템디자인공학과", "기계·시스템디자인공학과 자료실", "https://me.hongik.ac.kr/me/0702.do", "자료실"),
            hongik("civil", "건설환경공학과", "건설환경공학과", "https://civil.hongik.ac.kr/civil/0401.do", "학과공지"),
            hongik("civil-gen", "건설환경공학과", "건설환경공학과 일반공지", "https://civil.hongik.ac.kr/civil/0402.do", "일반공지"),

            // 건축도시대학 — 건축학부는 학교 본부와 다른 PHP CMS(arch.hongik.ac.kr)를 쓴다.
            arch("arch", "건축학부", "건축학부", "https://arch.hongik.ac.kr/kor/news/notice.php"),
            arch("arch-ev", "건축학부", "건축학부 행사", "https://arch.hongik.ac.kr/kor/news/event.php"),
            // 도시공학과. 프론트 TREE_DATA 노드 이름이 '도시학과'라 sourceId를 그쪽에 맞춘다. Imweb 사이트.
            imweb("urban", "도시학과", "도시공학과", "https://urban.hongik.ac.kr/114"),

            hongik("econ", "경제학부", "경제학부", "https://economics.hongik.ac.kr/economics/0401.do", "공지사항"),
            hongik("biz", "경영학부", "경영학부", "https://bizadmin.hongik.ac.kr/bizadmin/0401.do", "공지사항"),

            hongik("eng", "영어영문학과", "영어영문학과", "https://english.hongik.ac.kr/english/0401.do", "공지사항"),
            hongik("ger", "독어독문학과", "독어독문학과", "https://german.hongik.ac.kr/german/0401.do", "공지사항"),
            hongik("fra", "불어불문학과", "불어불문학과", "https://france.hongik.ac.kr/france/0401.do", "공지사항"),
            hongik("kor", "국어국문학과", "국어국문학과", "https://hkorean.hongik.ac.kr/hkorean/0401.do", "공지사항"),

            hongik("law", "법학부", "법학부", "https://law.hongik.ac.kr/law/0401.do", "공지사항"),
            hongik("law-job", "법학부", "법학부 취업정보", "https://law.hongik.ac.kr/law/0403.do", "취업정보"),

            hongik("edu", "교육학과", "교육학과", "https://edu.hongik.ac.kr/edu/0401.do", "공지사항"),
            hongik("koredu", "국어교육과", "국어교육과", "https://koredu.hongik.ac.kr/koredu/0401.do", "공지사항"),
            hongik("mathedu", "수학교육과", "수학교육과", "https://math.hongik.ac.kr/math/0401.do", "공지사항"),
            hongik("engedu", "영어교육과", "영어교육과", "https://engedu.hongik.ac.kr/engedu/0401.do", "공지사항"),
            hongik("hisedu", "역사교육과", "역사교육과", "https://hisedu.hongik.ac.kr/hisedu/0401.do", "공지사항"),

            hongik("orip", "동양화과", "동양화과", "https://orip.hongik.ac.kr/orip/0401.do", "공지사항"),
            hongik("painting", "회화과", "회화과", "https://painting.hongik.ac.kr/painting/0401.do", "공지사항"),
            hongik("printmk", "판화과", "판화과", "https://printmk.hongik.ac.kr/printmk/0401.do", "공지사항"),
            hongik("scu", "조소과", "조소과", "https://scu.hongik.ac.kr/scu/0401.do", "공지사항"),
            // 시각디자인전공(sidi.hongik.ac.kr)은 JS 렌더링 사이트라 config.mjs에서도 빠져 있다.
            hongik("id", "디자인학부", "산업디자인전공", "https://id.hongik.ac.kr/id/0401.do", "공지사항"),
            hongik("metalart", "금속조형디자인과", "금속조형디자인과", "https://metalart.hongik.ac.kr/metalart/0401.do", "공지사항"),
            hongik("cer", "도예유리과", "도예유리과", "https://cer.hongik.ac.kr/cer/0401.do", "공지사항"),
            hongik("waf", "목조형가구학과", "목조형가구학과", "https://waf.hongik.ac.kr/waf/0401.do", "공지사항"),
            hongik("textile", "섬유미술패션디자인과", "섬유미술패션디자인과", "https://textile.hongik.ac.kr/textile/0401.do", "공지사항"),
            hongik("art", "예술학과", "예술학과", "https://art.hongik.ac.kr/art/0401.do", "공지사항"),

            hongik("musical", "뮤지컬전공", "뮤지컬전공", "https://musical.hongik.ac.kr/musical/0501.do", "공지사항"),
            hongik("music", "실용음악전공", "실용음악전공", "https://music.hongik.ac.kr/music/0501.do", "공지사항"),

            hongik("iim", "디자인예술경영학부", "디자인예술경영학부", "https://iim.hongik.ac.kr/iim/0401.do", "공지사항")
    );

    /**
     * 대학공지는 분류(srCategoryId)마다 따로 받는다. boardKey는 여섯 개 모두 'univ'로 같다 —
     * articleNo가 학교 CMS 전체에서 유일해서 news.source_url(=상세 링크) 기준 중복 저장 방지에는 문제없다.
     */
    private static final List<String[]> UNIVERSITY_CATEGORIES = List.of(
            new String[]{"학사", "23"},
            new String[]{"장학", "24"},
            new String[]{"교수학습지원", "534"},
            new String[]{"학생상담", "535"},
            new String[]{"대학혁신지원사업", "536"},
            new String[]{"학생활동", "537"}
    );

    public static final List<BoardConfig> UNIVERSITY_BOARDS = UNIVERSITY_CATEGORIES.stream()
            .map(entry -> new BoardConfig(
                    "univ",
                    entry[0],
                    "대학공지 " + entry[0],
                    UNIVERSITY_NOTICE_URL + "?srCategoryId=" + entry[1],
                    UNIVERSITY_TABLE,
                    ParserType.HONGIK,
                    SEJONG_TITLE,
                    true,
                    UNIVERSITY_MAX_ITEMS
            ))
            .toList();

    public static final List<BoardConfig> ALL =
            Stream.concat(DEPARTMENT_BOARDS.stream(), UNIVERSITY_BOARDS.stream()).toList();

    private static BoardConfig hongik(String boardKey, String sourceId, String source, String listUrl, String tableSummary) {
        return new BoardConfig(boardKey, sourceId, source, listUrl, tableSummary, ParserType.HONGIK, null, false, DEPARTMENT_MAX_ITEMS);
    }

    private static BoardConfig arch(String boardKey, String sourceId, String source, String listUrl) {
        return new BoardConfig(boardKey, sourceId, source, listUrl, null, ParserType.ARCH, null, false, DEPARTMENT_MAX_ITEMS);
    }

    private static BoardConfig imweb(String boardKey, String sourceId, String source, String listUrl) {
        return new BoardConfig(boardKey, sourceId, source, listUrl, null, ParserType.IMWEB, null, false, DEPARTMENT_MAX_ITEMS);
    }

    private CrawlerBoards() {
    }
}
