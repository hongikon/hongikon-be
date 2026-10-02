package com.hongmap.hongmapbackend.crawler.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 크롤링 대상 게시판 목록. hongmap(프론트) scripts/crawler/config.mjs 를 그대로 포팅했다.
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
            // 기초과학과. 학과 공지 게시판(표 summary가 '학과공지사항')이 2026.10 확인 시점에 비어 있다 — 글이 올라오면 바로 수집된다.
            hongik("science", "기초과학과", "기초과학과", "https://science.hongik.ac.kr/science/0401.do", "학과공지사항"),

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
            // 조소과. URL·표 summary·마크업 모두 다른 학과와 같고 파서도 맞다 — 게시판 자체가 비어 있다
            // ("등록된 글이 없습니다", 2026.10 확인). 수집 0건은 정상이며 글이 올라오면 바로 수집된다.
            hongik("scu", "조소과", "조소과", "https://scu.hongik.ac.kr/scu/0401.do", "공지사항"),
            // 시각디자인전공(sidi.hongik.ac.kr)은 JS 렌더링 사이트라 config.mjs에서도 빠져 있다.
            hongik("id", "디자인학부", "산업디자인전공", "https://id.hongik.ac.kr/id/0401.do", "공지사항"),
            hongik("metalart", "금속조형디자인과", "금속조형디자인과", "https://metalart.hongik.ac.kr/metalart/0401.do", "공지사항"),
            hongik("cer", "도예유리과", "도예유리과", "https://cer.hongik.ac.kr/cer/0401.do", "공지사항"),
            hongik("waf", "목조형가구학과", "목조형가구학과", "https://waf.hongik.ac.kr/waf/0401.do", "공지사항"),
            hongik("textile", "섬유미술패션디자인과", "섬유미술패션디자인과", "https://textile.hongik.ac.kr/textile/0401.do", "공지사항"),
            hongik("art", "예술학과", "예술학과", "https://art.hongik.ac.kr/art/0401.do", "공지사항"),
            // 앱은 '자율전공'을 미술대학 아래 두지만 미술대학 자율전공만의 게시판은 없다.
            // 서울캠퍼스 자율전공(fm.hongik.ac.kr) 공지가 자율전공 학생 공지 게시판이라 그쪽을 받는다.
            hongik("fm", "자율전공", "서울캠퍼스 자율전공", "https://fm.hongik.ac.kr/fm/0401.do", "공지사항"),

            hongik("musical", "뮤지컬전공", "뮤지컬전공", "https://musical.hongik.ac.kr/musical/0501.do", "공지사항"),
            hongik("music", "실용음악전공", "실용음악전공", "https://music.hongik.ac.kr/music/0501.do", "공지사항"),

            hongik("iim", "디자인예술경영학부", "디자인예술경영학부", "https://iim.hongik.ac.kr/iim/0401.do", "공지사항"),

            // 바이오헬스융합학부 = 바이오헬스 혁신융합대학사업단 사이트(Imweb). 메뉴 '공지사항'(/22)과 '학사공지'(/notice)는 같은 게시판이다.
            imweb("biohealth", "바이오헬스융합학부", "바이오헬스융합학부", "https://biohealth.hongik.ac.kr/22"),
            // 디자인엔지니어링 융합전공은 자체 사이트(smpd.hongik.ac.kr)가 있다.
            hongik("smpd", "디자인엔지니어링전공", "디자인엔지니어링전공", "https://smpd.hongik.ac.kr/smpd/0401.do", "공지사항")
    );

    /**
     * 자체 공지 게시판이 없어 상위(주관) 학과·학부 게시판을 같이 보는 앱 게시판 id → 그 상위 게시판 sourceId.
     * 같은 글을 두 sourceId로 저장할 수는 없으므로(news.source_url UNIQUE) 크롤러는 상위 게시판으로 한 번만 저장하고,
     * 소식 조회(GET /news?sourceId=)·구독 검증·새 소식 푸시 대상 계산에서 이 표로 펼친다.
     * 키는 프론트 TREE_DATA 리프 id와 정확히 같아야 한다.
     */
    public static final Map<String, String> SOURCE_ALIASES = Map.of(
            // 디자인·예술경영학부는 전공이 둘이지만 공지 게시판은 학부 하나뿐이다.
            "디자인경영전공", "디자인예술경영학부",
            "예술경영전공", "디자인예술경영학부",
            // 융합전공 — 자체 사이트가 없고 주관 학과 게시판에 공지한다(학교 융합전공 안내의 주관학과 기준).
            "데이터사이언스전공", "산업데이터공학과",
            "사물인터넷공학전공", "전자전기공학부",
            "지능로봇공학전공", "기계시스템디자인공학과"
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

    /** 구독할 수 있는 게시판 id 전체: 크롤러 게시판의 sourceId + 상위 게시판을 빌려 쓰는 별칭. */
    public static final Set<String> KNOWN_SOURCE_IDS = Stream.concat(
                    ALL.stream().map(BoardConfig::sourceId),
                    SOURCE_ALIASES.keySet().stream())
            .collect(Collectors.toUnmodifiableSet());

    /**
     * 소식 조회용. 앱이 보낸 게시판 id 목록을 news.source_id에 실제로 저장되는 값으로 바꾼다 —
     * 별칭이면 상위 게시판 id로 바꾸고, 아니면 그대로 둔다(순서 유지, 중복 제거).
     */
    public static List<String> resolveStoredSourceIds(Collection<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) return List.of();
        Set<String> resolved = new LinkedHashSet<>();
        for (String id : sourceIds) {
            resolved.add(SOURCE_ALIASES.getOrDefault(id, id));
        }
        return List.copyOf(resolved);
    }

    /**
     * 새 소식 푸시용. 이 sourceId로 저장된 소식을 받아야 하는 구독 게시판 id들 — 자기 자신 + 이 게시판을 빌려 쓰는 별칭.
     */
    public static List<String> subscriberSourceIds(String storedSourceId) {
        List<String> ids = new ArrayList<>();
        ids.add(storedSourceId);
        SOURCE_ALIASES.forEach((alias, parent) -> {
            if (parent.equals(storedSourceId)) ids.add(alias);
        });
        return ids;
    }

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
