package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 질문 속 학과·기관 이름 → 공지 게시판(news.source_id). '컴공 동문장학금', '화공과 특강'처럼 학과를 말하면 그 게시판 공지를 먼저 찾는다
 * (10-25 운영진 요청: 학과 크롤링 공지의 장학금·행사도 답에 반영). 학교 전체 대학공지(학사·장학 …)는 학과가 아니라 넣지 않는다.
 */
final class AskBoards {

    /** 학생들이 쓰는 줄임말 → 게시판. 정식 이름·'○○과'·'○○학부' 꼴은 {@link #ALIASES} 를 만들 때 자동으로 넣는다. */
    private static final Map<String, String> SHORT = Map.ofEntries(
            Map.entry("컴공", "컴퓨터공학과"), Map.entry("컴퓨터공학", "컴퓨터공학과"),
            Map.entry("전전", "전자전기공학부"), Map.entry("전자전기", "전자전기공학부"), Map.entry("전자공학", "전자전기공학부"),
            Map.entry("신소재", "신소재공학전공"), Map.entry("화공", "화학공학전공"), Map.entry("화학공학", "화학공학전공"),
            Map.entry("산공", "산업데이터공학과"), Map.entry("산데공", "산업데이터공학과"), Map.entry("산데", "산업데이터공학과"),
            Map.entry("산업데이터", "산업데이터공학과"), Map.entry("산업·데이터", "산업데이터공학과"), Map.entry("데이터사이언스", "산업데이터공학과"),
            // 11-02 운영진 요청: 학생들이 쓰는 줄임말(학과 이름 단어마다 첫 글자). 일반 낱말과 겹치는 것(영문·회화·경영·경제·건축·동화)은 넣지 않는다.
            Map.entry("기시디", "기계시스템디자인공학과"), Map.entry("기계·시스템", "기계시스템디자인공학과"),
            Map.entry("건환", "건설환경공학과"), Map.entry("실건", "건축학부"), Map.entry("실내건축", "건축학부"), Map.entry("도공", "도시학과"),
            Map.entry("국문과", "국어국문학과"), Map.entry("영어영문", "영어영문학과"),
            Map.entry("산디", "디자인학부"), Map.entry("산업디자인", "디자인학부"), Map.entry("시각디자인", "디자인학부"),
            Map.entry("금조디", "금속조형디자인과"), Map.entry("금조", "금속조형디자인과"),
            Map.entry("목조디", "목조형가구학과"), Map.entry("목가", "목조형가구학과"), Map.entry("목조형가구", "목조형가구학과"),
            Map.entry("섬패디", "섬유미술패션디자인과"), Map.entry("패디", "섬유미술패션디자인과"), Map.entry("섬미", "섬유미술패션디자인과"),
            Map.entry("도예유리", "도예유리과"),
            Map.entry("기디공", "기계시스템디자인공학과"), Map.entry("기계공학", "기계시스템디자인공학과"), Map.entry("기계시스템", "기계시스템디자인공학과"),
            Map.entry("건환공", "건설환경공학과"), Map.entry("건설환경", "건설환경공학과"), Map.entry("토목", "건설환경공학과"),
            Map.entry("건축과", "건축학부"), Map.entry("건축학과", "건축학부"), Map.entry("도시공학", "도시학과"),
            Map.entry("경제과", "경제학부"), Map.entry("경영과", "경영학부"), Map.entry("경영대", "경영학부"),
            Map.entry("독문", "독어독문학과"), Map.entry("불문", "불어불문학과"), Map.entry("국어국문", "국어국문학과"),
            Map.entry("법대", "법학부"), Map.entry("법학과", "법학부"),
            Map.entry("국교", "국어교육과"), Map.entry("수교", "수학교육과"), Map.entry("영교", "영어교육과"), Map.entry("역교", "역사교육과"),
            Map.entry("동양화", "동양화과"), Map.entry("판화", "판화과"), Map.entry("조소", "조소과"),
            Map.entry("시디", "디자인학부"), Map.entry("디자인학과", "디자인학부"), Map.entry("금속조형", "금속조형디자인과"),
            Map.entry("도예", "도예유리과"), Map.entry("목조형", "목조형가구학과"), Map.entry("섬유미술", "섬유미술패션디자인과"), Map.entry("패션디자인", "섬유미술패션디자인과"),
            Map.entry("예술학", "예술학과"), Map.entry("캠자", "자율전공"), Map.entry("캠퍼스자율", "자율전공"), Map.entry("자율전공", "자율전공"),
            Map.entry("뮤지컬", "뮤지컬전공"), Map.entry("실음", "실용음악전공"), Map.entry("실용음악", "실용음악전공"),
            Map.entry("디예경", "디자인예술경영학부"), Map.entry("디자인경영", "디자인예술경영학부"), Map.entry("예술경영", "디자인예술경영학부"),
            // 11-14: 옛 이름 디자인경영융합학부의 줄임말(학생들이 여전히 쓴다).
            Map.entry("디경융", "디자인예술경영학부"), Map.entry("디자인경영융합", "디자인예술경영학부"),
            Map.entry("바이오헬스", "바이오헬스융합학부"), Map.entry("디자인엔지니어링", "디자인엔지니어링전공"),
            Map.entry("국제교류", "국제교류실"), Map.entry("교환학생", "국제교류실"),
            Map.entry("취업진로", "취업진로공지"), Map.entry("취업지원", "취업진로공지"), Map.entry("채용", "채용정보"),
            Map.entry("글로벌역량", "글로벌역량지원사업"));

    /**
     * 1학년은 학부로 함께 다니고 2학년에 전공을 나누는 학부 → 그 전공 게시판들. 학회·졸업요건처럼 전공마다 따로인 것은
     * 학부 이름으로 물으면 두 전공 것을 함께, 한 전공 이름으로 물으면 그 전공 것만 보인다(11-03 '신소재화공 학회'와 '신소재 학회' 답이 같던 것).
     */
    static final Map<String, List<String>> GROUPS = Map.of(
            "신소재·화공시스템공학부", List.of("신소재공학전공", "화학공학전공"));

    /** 학부 이름 꼴 → 학부. 전공 줄임말('신소재'·'화학공학')보다 길어 먼저 맞는다. */
    private static final Map<String, String> GROUP_ALIASES = Map.of(
            "신소재화공", "신소재·화공시스템공학부",
            "신소재화학공학", "신소재·화공시스템공학부");

    /** 공백·가운뎃점 없앤 이름 → 게시판(또는 {@link #GROUPS} 학부). 긴 이름부터 맞춘다('디자인학부'가 '디자인'보다 먼저). */
    static final Map<String, String> ALIASES = build();

    /** 이름을 맞출 때 무시하는 글자('신소재 화공', '신소재·화공'). */
    private static final String SEPARATORS = "[\\s·ㆍ・]";

    private AskBoards() {
    }

    private static Map<String, String> build() {
        Map<String, String> aliases = new LinkedHashMap<>(GROUP_ALIASES);
        for (BoardConfig board : CrawlerBoards.DEPARTMENT_BOARDS) {
            String id = board.sourceId();
            aliases.put(id, id);
            String base = id.replaceAll("(학과|학부|전공|과)$", "");
            if (base.length() >= 2 && !base.equals(id)) {
                aliases.putIfAbsent(base + "과", id);
                aliases.putIfAbsent(base + "학과", id);
                aliases.putIfAbsent(base + "학부", id);
            }
        }
        CrawlerBoards.SOURCE_ALIASES.forEach(aliases::putIfAbsent);
        SHORT.forEach(aliases::putIfAbsent);
        List<Map.Entry<String, String>> sorted = new ArrayList<>(aliases.entrySet());
        sorted.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> -e.getKey().length()));
        Map<String, String> out = new LinkedHashMap<>();
        sorted.forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    /** 질문에 나온 학과·기관 게시판과 그 이름(질문에서 뺄 낱말). 없으면 empty. */
    static Optional<Match> find(String question) {
        if (question == null) {
            return Optional.empty();
        }
        String squashed = question.replaceAll(SEPARATORS + "+", "");
        for (Map.Entry<String, String> alias : ALIASES.entrySet()) {
            for (int at = squashed.indexOf(alias.getKey()); at >= 0; at = squashed.indexOf(alias.getKey(), at + 1)) {
                int after = at + alias.getKey().length();
                // '교육과정', '디자인학과목'처럼 '○○과' 뒤에 낱말이 이어지면 학과 이름이 아니다.
                if (alias.getKey().endsWith("과") && after < squashed.length() && "정목제".indexOf(squashed.charAt(after)) >= 0) {
                    continue;
                }
                String name = alias.getValue();
                return Optional.of(new Match(name, GROUPS.getOrDefault(name, List.of(name)), alias.getKey()));
            }
        }
        return Optional.empty();
    }

    /**
     * 고르기 메시지에 넣을 한 줄 — 질문 속 학과 줄임말이 홍익대 학과라는 것. 없으면 "".
     * 11-02: '산데공 학회'를 고르기가 학교와 무관한 질문(off_topic)으로 거절했다 — '산데공'을 학과로 몰랐다.
     */
    static String hint(String question) {
        return find(question)
                .map(match -> "질문 속 '" + match.alias() + "'은(는) 홍익대 " + match.name() + "(학과·기관)이다 — 그 학과의 학교생활 질문이다.\n")
                .orElse("");
    }


    /** name: 학과·기관(또는 학부) 이름, boards: 찾을 게시판(학부면 전공 여럿), alias: 질문에서 맞은 이름(공백·가운뎃점 뺀 꼴). */
    record Match(String name, List<String> boards, String alias) {

        /** 첫 게시판(학과 하나면 그 게시판). */
        String board() {
            return boards.get(0);
        }

        /** 학부 이름으로 물었다(전공이 여럿). */
        boolean group() {
            return boards.size() > 1;
        }

        /** 한 전공을 물었을 때 같은 학부의 다른 전공 — 그쪽 홈페이지 쪽은 답 근거에서 뺀다. 학부로 물었거나 학부가 없으면 빈 목록. */
        List<String> siblings() {
            if (group()) {
                return List.of();
            }
            return GROUPS.values().stream().filter(members -> members.contains(board()))
                    .flatMap(List::stream).filter(member -> !member.equals(board())).toList();
        }

        /** 질문에서 맞은 이름을 지운 나머지(이름 사이 공백·가운뎃점도 함께) — 그 학과 안에서 찾을 낱말. */
        String strip(String question) {
            StringBuilder regex = new StringBuilder();
            alias.codePoints().forEach(c -> {
                if (!regex.isEmpty()) {
                    regex.append(SEPARATORS).append('*');
                }
                regex.append(Pattern.quote(new String(Character.toChars(c))));
            });
            return question.replaceAll(regex.toString(), " ");
        }
    }
}
