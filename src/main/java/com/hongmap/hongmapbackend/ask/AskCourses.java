package com.hongmap.hongmapbackend.ask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 과목 DB·줄임말(11-10, FE docs/ask-course-db-plan.md). 데이터는 FE `scripts/ask/courses/extract.py`·`aliases.py` 가 만든
 * `ask/courses.json`(2026 교과과정책자 학과 교과과정 표 — 학과·학년·학기·학점·이수구분)과 `ask/aliases.json`
 * (운영진 줄임말 curated + 과목명 뜻 단위 첫 글자 규칙 auto). 처음 쓸 때 한 번 읽는다(없으면 빈 데이터 — 물어보기는 그대로 돈다).
 * 책자는 '교육과정'이다 — 이번 학기 개설·분반·교수는 수강신청 시스템에 있다.
 */
final class AskCourses {

    private static final Logger log = LoggerFactory.getLogger(AskCourses.class);
    static final String SOURCE_REF = "courses:2026";
    static final String SOURCE_TITLE = "2026 교과과정책자(학과 교과과정)";
    static final String SOURCE_URL = "https://wwwce.hongik.ac.kr/wwwce/0302.do?mode=view&articleNo=149240";
    static final int MAX_LINES = 45;
    /** 줄임말 뒤에 붙는 조사 — '컴프는', '중동에서'. */
    private static final List<String> JOSA = List.of("", "은", "는", "이", "가", "을", "를", "에", "에서", "도", "랑", "이랑", "하고", "의",
            "으로", "로", "만", "이야", "야", "이에요", "예요", "은요", "는요", "부터", "까지");

    record Course(String code, String name, String dept, int grade, int term, String credits, String category,
                  String campus, String src, boolean checked) {
    }

    /** @param aliases 줄임말 → 정식 이름들(둘 이상이면 되묻는다), curated 는 운영진 확인. */
    record Data(List<Course> courses, Map<String, List<String>> aliases, Set<String> curated) {
        static final Data EMPTY = new Data(List.of(), Map.of(), Set.of());
    }

    private static volatile Data data;

    private AskCourses() {
    }

    static Data data() {
        Data loaded = data;
        if (loaded == null) {
            synchronized (AskCourses.class) {
                if (data == null) {
                    data = load(JsonMapper.builder().build());
                }
                loaded = data;
            }
        }
        return loaded;
    }

    static Data load(JsonMapper mapper) {
        try (InputStream courses = new ClassPathResource("ask/courses.json").getInputStream();
             InputStream aliases = new ClassPathResource("ask/aliases.json").getInputStream()) {
            List<Course> list = new ArrayList<>();
            for (JsonNode c : mapper.readTree(courses).path("courses")) {
                list.add(new Course(c.path("code").asString(null), c.path("name").asString(""), c.path("dept").asString(""),
                        c.path("grade").asInt(), c.path("term").asInt(), c.path("credits").asString(""), c.path("category").asString(null),
                        c.path("campus").asString("서울"), c.path("src").asString(""), c.path("checked").asBoolean(false)));
            }
            JsonNode a = mapper.readTree(aliases);
            Map<String, List<String>> map = new LinkedHashMap<>();
            Set<String> curated = new LinkedHashSet<>();
            for (JsonNode x : a.path("curated")) {
                // full: 이름 하나 또는 여러 개(대물 → 대학물리·대학물리실험).
                List<String> full = new ArrayList<>();
                if (x.path("full").isArray()) {
                    x.path("full").forEach(f -> full.add(f.asString()));
                } else {
                    full.add(x.path("full").asString());
                }
                map.put(x.path("short").asString(), List.copyOf(full));
                curated.add(x.path("short").asString());
            }
            for (JsonNode x : a.path("auto")) {
                List<String> full = new ArrayList<>();
                x.path("full").forEach(f -> full.add(f.asString()));
                map.putIfAbsent(x.path("short").asString(), full);
            }
            return new Data(List.copyOf(list), map, curated);
        } catch (Exception e) {
            log.warn("물어보기 과목 DB(ask/courses.json·aliases.json)를 읽지 못해 끈다: {}", e.toString());
            return Data.EMPTY;
        }
    }

    static String squash(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }

    /** 질문 속 줄임말 → 정식 이름(순서 유지). 낱말 앞부분이 줄임말이고 나머지가 조사일 때만('인공지능'은 '인공'이 아니다). */
    static Map<String, List<String>> aliasesIn(String text, Data d) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (text == null) {
            return out;
        }
        for (String token : text.split("[\\s,.?!~·/()\"'’]+")) {
            for (int len = Math.min(4, token.length()); len >= 2; len--) {
                String head = token.substring(0, len);
                List<String> full = d.aliases().get(head);
                if (full != null && JOSA.contains(token.substring(len)) && !full.contains(head)) {
                    out.putIfAbsent(head, full);
                    break;
                }
            }
        }
        return out;
    }

    /** 분류·검색·답 쓰기에 붙일 한 줄 — '[질문 속 줄임말] 컴프 = 컴퓨터프로그래밍; 인공 = 인간공학·인지공학(여러 과목 — 어느 것인지 묻는다)'. */
    static String aliasLine(String question) {
        Map<String, List<String>> found = aliasesIn(question, data());
        if (found.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        found.forEach((s, full) -> parts.add(s + " = " + String.join("·", full)
                + (full.size() > 1 ? "(여러 과목 — 어느 것인지 묻는다)" : "")));
        return "[질문 속 줄임말] " + String.join("; ", parts) + "\n";
    }

    /** 검색어 넓히기 — 질문 뒤에 줄임말의 정식 이름을 붙인다(RAG·낱말 검색이 '중동'으로 '중앙동아리' 안내를 찾게). */
    static String expand(String question) {
        Map<String, List<String>> found = aliasesIn(question, data());
        if (found.isEmpty()) {
            return question;
        }
        StringBuilder out = new StringBuilder(question);
        found.values().forEach(full -> out.append(' ').append(String.join(" ", full)));
        return out.toString();
    }

    private static String deptStem(String dept) {
        return squash(dept).replaceAll("(학과|학부|전공|과)$", "");
    }

    /**
     * 과목 찾기. query: 과목명·줄임말·학수번호(비우면 학과·학년 과목 목록). dept: 학과·전공 이름(줄여도 됨 — 포함 일치). grade·term: 학년·학기(없으면 null).
     * campus: '세종'을 주지 않으면 서울. 결과는 학과 → 학년 → 학기 순 한 줄씩, 최대 {@link #MAX_LINES}.
     */
    static String search(String query, String dept, Integer grade, Integer term, String campus, Data d) {
        String q = squash(query);
        Set<String> names = new LinkedHashSet<>();
        if (!q.isEmpty()) {
            names.add(q);
            List<String> full = d.aliases().get(query.strip());
            if (full != null) {
                full.forEach(f -> names.add(squash(f)));
            }
            aliasesIn(query, d).values().forEach(fs -> fs.forEach(f -> names.add(squash(f))));
        }
        String stem = dept == null ? "" : deptStem(dept);
        if (names.isEmpty() && stem.isEmpty()) {
            return "과목명·줄임말·학수번호나 학과를 주세요.";
        }
        String wantCampus = campus != null && campus.contains("세종") ? "세종" : "서울";
        List<Course> hits = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Course c : d.courses()) {
            if (!c.campus().equals(wantCampus)
                    || (!stem.isEmpty() && !squash(c.dept()).contains(stem))
                    || (grade != null && c.grade() != grade)
                    || (term != null && c.term() != term)) {
                continue;
            }
            String name = squash(c.name()).replaceAll("\\([^)]*\\)", "");
            boolean nameOk = names.isEmpty() || q.equals(c.code())
                    || names.stream().anyMatch(n -> name.contains(n.replaceAll("\\([^)]*\\)", "")));
            if (nameOk && seen.add(c.dept() + c.grade() + c.term() + c.name())) {
                hits.add(c);
            }
        }
        if (hits.isEmpty()) {
            return "2026 교과과정책자(" + wantCampus + ")에서 맞는 과목을 찾지 못했어요.";
        }
        hits.sort(Comparator.comparing(Course::dept).thenComparingInt(Course::grade).thenComparingInt(Course::term).thenComparing(Course::name));
        StringBuilder out = new StringBuilder(SOURCE_REF).append(" · ").append(SOURCE_TITLE).append(" 기준(").append(wantCampus)
                .append(") — 교육과정이다. 이번 학기 개설·분반·시간은 수강신청 시스템에서 확인한다.\n");
        int lines = 0;
        for (Course c : hits) {
            if (lines++ >= MAX_LINES) {
                out.append("(과목이 더 있어요 — 학과·학년·학기로 좁혀 다시 찾는다)\n");
                break;
            }
            out.append("- ").append(c.dept()).append(' ').append(c.grade()).append("학년 ").append(c.term()).append("학기 · ")
                    .append(c.name()).append(c.code() == null ? "" : " (" + c.code() + ")").append(" · ").append(c.credits()).append("학점")
                    .append(c.category() == null ? "" : " · " + c.category())
                    .append(c.checked() ? "" : " · 책자 합계 검산 안 됨(원문 " + c.src().replace("2026 교과과정책자 PDF ", "") + " 확인)").append('\n');
        }
        return out.toString();
    }
}
