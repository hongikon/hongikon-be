package com.hongmap.hongmapbackend.ask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 시설 '종류' 질문 알아보기(11-12 사용자 피드백). '열람실 어디 있어', '공부할 수 있는 곳', '프린트 어디서 해', '편의점 어디'처럼
 * 한 종류의 시설을 물으면 지도 시설 중 그 종류 전부를 넘겨야 한다 — 낱말이 많이 맞은 순 8개로 자르면(10-16 방식) 9곳인 프린터가
 * 2곳만, '공부'는 아무것도 안 나왔다. 여기서는 질문 낱말 → 지도 시설 종류(campus_facilities.kind)만 정한다.
 */
final class AskFacilityKinds {

    /** 낱말 묶음 → 지도 시설 종류. 순서는 답에 보일 종류 순서. */
    private record Group(Pattern words, List<String> kinds) {
    }

    private static final List<Group> GROUPS = List.of(
            // '공부할 곳'은 열람실만이 아니다 — 스터디룸·라운지도 공부하는 곳이다(사용자 피드백).
            new Group(Pattern.compile("공부|자습|스터디(?!룸)"), List.of("열람실", "스터디룸", "라운지")),
            new Group(Pattern.compile("열람실|독서실"), List.of("열람실")),
            new Group(Pattern.compile("스터디룸|학습실|세미나실|토론실|프레젠테이션룸"), List.of("스터디룸")),
            new Group(Pattern.compile("라운지"), List.of("라운지")),
            new Group(Pattern.compile("프린트|프린터|출력|인쇄|복사"), List.of("프린터")),
            new Group(Pattern.compile("편의점"), List.of("편의점")),
            new Group(Pattern.compile("카페|커피"), List.of("카페")),
            new Group(Pattern.compile("식당|학식|밥|먹을"), List.of("식당")));

    /** '증명서 출력'은 프린터가 아니라 증명서 발급기다. */
    private static final Pattern CERTIFICATE = Pattern.compile("증명서");

    /** 좁히기에 쓰지 않는 낱말 — 물음 말투·학교 전체를 가리키는 말. */
    private static final Set<String> STOP_WORDS = Set.of(
            "어디", "어디야", "어디서", "어디에", "어딨어", "어디있어", "어디에서", "있어", "있어요", "있나", "있나요", "있니", "있는", "있는지",
            "알려줘", "알려", "알려주세요", "학교", "교내", "캠퍼스", "홍대", "홍익대", "홍익대학교", "근처", "가능", "가능한", "장소", "공간",
            "할", "하는", "해", "해요", "하기", "하려면", "좋은", "수", "목록", "전부", "모두", "다", "뭐", "뭐가", "뭐있어", "어떤", "곳", "데",
            "되는", "돼", "쓸", "이용", "위치", "말고", "또", "더");

    /** 질문의 건물 줄임말 → 지도 건물 이름에 있는 말. */
    private static final Map<String, String> ALIASES = Map.of("중도", "중앙도서관", "학관", "학생회관");

    /** 낱말 끝 조사('K동에', '중도에서', 'T동은') — 지도 건물 이름과 맞추려고 뗀다. 2글자는 그대로('공대'). */
    private static final Pattern PARTICLE = Pattern.compile("(?<=..)(에서|에|의|은|는|이|가|을|를|도)$");

    private AskFacilityKinds() {
    }

    /** 질문이 묻는 시설 종류(없으면 빈 목록). */
    static List<String> kinds(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        boolean certificate = CERTIFICATE.matcher(question).find();
        Set<String> kinds = new LinkedHashSet<>();
        for (Group group : GROUPS) {
            if (certificate && group.kinds().contains("프린터")) {
                continue;
            }
            if (group.words().matcher(question).find()) {
                kinds.addAll(group.kinds());
            }
        }
        return List.copyOf(kinds);
    }

    /**
     * 답 쓰기 메시지에 붙일 줄 — 종류 질문이면 search_facilities 를 부르게 한다. 안내 원문(학교 홈페이지 편의시설 글)에 몇 곳만 적혀 있으면
     * 모델이 도구를 안 부르고 그 몇 곳만 답했다('열람실' → 중도·T동만, 11-12). 학식 메뉴 질문('오늘 학식')은 메뉴를 따로 넘기니 '어디'를 묻지 않으면 붙이지 않는다.
     */
    static String contextLine(String question) {
        List<String> kinds = kinds(question);
        if (kinds.isEmpty() || (kinds.equals(List.of("식당")) && !question.contains("어디")
                && !AskCafeteriaQuestion.dates(question, java.time.LocalDate.now()).isEmpty())) {
            return "";
        }
        return "[시설 종류 질문] " + String.join("·", kinds) + " — 안내 원문만 보고 답하지 말고 search_facilities 에 학생 질문을 그대로 넣어 찾는다. "
                + "결과가 [종류별 전체 목록]이면 그곳을 모두 건물별로 보인다(일부만 고르지 않는다). 안내 원문에만 있는 곳도 함께 넣는다.\n\n";
    }

    /**
     * 종류 낱말·물음 말투를 뺀 나머지 낱말(소문자, 2글자 이상) — 'K동 열람실'의 'k동', '24시간 열람실'의 '24시간'.
     * 이 낱말이 맞는 시설이 있으면 그 시설만, 없으면 그 종류 전부를 보인다.
     */
    static List<String> narrowingWords(String question) {
        List<String> words = new ArrayList<>();
        if (question == null) {
            return words;
        }
        for (String raw : question.split("[\\s,·/()?!.~]+")) {
            String word = PARTICLE.matcher(raw.toLowerCase()).replaceFirst("");
            if (word.length() < 2 || STOP_WORDS.contains(word) || isKindWord(word)) {
                continue;
            }
            words.add(ALIASES.getOrDefault(word, word));
        }
        return words;
    }

    private static boolean isKindWord(String word) {
        return GROUPS.stream().anyMatch(group -> group.words().matcher(word).find());
    }
}
