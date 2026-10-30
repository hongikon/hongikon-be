package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.crawler.config.BoardConfig;
import com.hongmap.hongmapbackend.crawler.config.CrawlerBoards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            Map.entry("산공", "산업데이터공학과"), Map.entry("산업데이터", "산업데이터공학과"), Map.entry("데이터사이언스", "산업데이터공학과"),
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
            Map.entry("바이오헬스", "바이오헬스융합학부"), Map.entry("디자인엔지니어링", "디자인엔지니어링전공"),
            Map.entry("국제교류", "국제교류실"), Map.entry("교환학생", "국제교류실"),
            Map.entry("취업진로", "취업진로공지"), Map.entry("취업지원", "취업진로공지"), Map.entry("채용", "채용정보"),
            Map.entry("글로벌역량", "글로벌역량지원사업"));

    /** 공백 없앤 이름 → 게시판. 긴 이름부터 맞춘다('디자인학부'가 '디자인'보다 먼저). */
    static final Map<String, String> ALIASES = build();

    private AskBoards() {
    }

    private static Map<String, String> build() {
        Map<String, String> aliases = new LinkedHashMap<>();
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
        String squashed = question.replaceAll("\\s+", "");
        for (Map.Entry<String, String> alias : ALIASES.entrySet()) {
            for (int at = squashed.indexOf(alias.getKey()); at >= 0; at = squashed.indexOf(alias.getKey(), at + 1)) {
                int after = at + alias.getKey().length();
                // '교육과정', '디자인학과목'처럼 '○○과' 뒤에 낱말이 이어지면 학과 이름이 아니다.
                if (alias.getKey().endsWith("과") && after < squashed.length() && "정목제".indexOf(squashed.charAt(after)) >= 0) {
                    continue;
                }
                return Optional.of(new Match(alias.getValue(), alias.getKey()));
            }
        }
        return Optional.empty();
    }

    record Match(String board, String alias) {
    }
}
