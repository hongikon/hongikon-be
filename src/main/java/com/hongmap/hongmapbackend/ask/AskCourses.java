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
import java.util.Optional;
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

    /** category: 정규 이수구분(전공필수·전공선택·전공기초·MSC·교양·교양필수·교양선택·일반선택·교직·특성화교양), categoryDetail: 영역(과학·핵심교양 …). */
    record Course(String code, String name, String dept, int grade, int term, String credits, String category,
                  String categoryDetail, String campus, String src, boolean checked) {
    }

    /** 결과 모양: LIST = 과목 줄, COUNT_BY_DEPT = 조건에 맞는 과목을 모든 행에서 세어 학과별 과목 수·학점 합(11-12). */
    enum Mode {
        LIST, COUNT_BY_DEPT;

        static Mode of(String text) {
            return text != null && squash(text).replace("_", "").contains("count") ? COUNT_BY_DEPT : LIST;
        }
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
                        c.path("categoryDetail").asString(null), c.path("campus").asString("서울"), c.path("src").asString(""), c.path("checked").asBoolean(false)));
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

    /** 이수구분 낱말(줄임말 포함) → 정규 이름. 모르면 null. '교양'·'전공'은 묶음(교양필수·교양선택 …, 전공필수·전공선택·전공기초). */
    static String normalizeCategory(String text) {
        String t = squash(text);
        if (t.isEmpty()) {
            return null;
        }
        return switch (t) {
            case "전공필수", "전필", "필수", "전공필수과목" -> "전공필수";
            case "전공선택", "전선", "전공선택과목" -> "전공선택";
            case "전공기초" -> "전공기초";
            case "교양필수", "교필" -> "교양필수";
            case "교양선택", "교선" -> "교양선택";
            case "일반선택", "일선" -> "일반선택";
            case "msc", "엠에스씨" -> "MSC";
            case "교직" -> "교직";
            case "특성화교양" -> "특성화교양";
            case "교양", "전문교양", "기본소양" -> "교양";
            case "전공" -> "전공";
            default -> null;
        };
    }

    /** 필터(정규 이름)에 과목 이수구분이 맞는지 — '교양'은 교양 묶음, '전공'은 전공 묶음. */
    static boolean categoryMatches(String filter, Course c) {
        if (filter == null) {
            return true;
        }
        String cat = c.category();
        if (cat == null) {
            return false;
        }
        return switch (filter) {
            case "교양" -> cat.startsWith("교양") || cat.equals("특성화교양");
            case "전공" -> cat.startsWith("전공");
            default -> cat.equals(filter);
        };
    }

    /** 질문 속 이수구분 낱말 — 긴 이름은 어디서든, 줄임말(전필·전선·교필·교선·일선)은 앞뒤가 다른 낱말이 아닐 때만(조사는 허용). */
    private static final java.util.regex.Pattern CATEGORY_WORD = java.util.regex.Pattern.compile(
            "(전공\\s*필수|전공\\s*선택|전공\\s*기초|교양\\s*필수|교양\\s*선택|일반\\s*선택|특성화\\s*교양|MSC|msc)"
                    + "|(?<![가-힣])(전필|전선|교필|교선|일선|교직)(?=(이|가|은|는|을|를|만|도|과목|으로|로)?(?![가-힣]))");
    /** 집계 질문에 섞여 오는 낱말 — 과목명 검색어에서 뺀다('전필이 가장 많은 학과' → 이름 조건 없음). 낱말 통째로만. */
    private static final Set<String> COUNT_WORDS = Set.of("학과", "학부", "전공", "과", "어느", "어떤", "무슨", "가장", "제일", "많은", "많이", "적은",
            "있는", "있어", "없는", "찾아줘", "찾아", "알려줘", "몇", "개", "학점", "합", "합계", "총", "개수", "과목수", "얼마나", "상관없이",
            "상관", "없이", "전체", "모든", "모두", "곳", "데", "어디", "순서", "순위", "많아", "돼", "되는", "들", "중", "중에", "중에서");
    /** 세거나 더하는 질문 — 이름 조건이 없으면 학과·학년 목록 대신 집계로 답한다('컴공 3학년 전공필수 학점 합'). */
    private static final java.util.regex.Pattern COUNT_QUESTION = java.util.regex.Pattern.compile(
            "몇\\s*학점|학점\\s*(합|총|합계|다\\s*더)|몇\\s*과목|몇\\s*개|과목\\s*수|가장|제일|어느\\s*학과|어떤\\s*학과|무슨\\s*학과|있는\\s*학과|학과\\s*상관|학과별");
    private static final java.util.regex.Pattern JOSA_TAIL = java.util.regex.Pattern.compile("(이야|이에요|예요|에서|으로|이|가|은|는|을|를|에|의|도|만|로|야|요)$");

    /**
     * 과목 찾기. query: 과목명·줄임말·학수번호(비우면 학과·학년 과목 목록). dept: 학과·전공 이름(줄여도 됨 — 포함 일치). grade·term: 학년·학기(없으면 null).
     * campus: '세종'을 주지 않으면 서울. 결과는 학과 → 학년 → 학기 순 한 줄씩, 최대 {@link #MAX_LINES}.
     */
    static String search(String query, String dept, Integer grade, Integer term, String campus, Data d) {
        return search(query, dept, grade, term, null, campus, Mode.LIST, d);
    }

    /**
     * 구조화 검색(11-12). 서버가 거르고 센다 — 모델은 칸만 채운다.
     * category: 이수구분(전필·전공필수·전선·교양·MSC … 줄임말 가능). mode: LIST(과목 줄) 또는 COUNT_BY_DEPT(모든 행을 세어 학과별 과목 수·학점).
     * query 에 섞여 온 학년·학기·학과·이수구분 낱말은 칸으로 옮긴다. 이름 조건도 학과도 없고 이수구분·학년만 있으면 COUNT_BY_DEPT 로 답한다.
     */
    static String search(String query, String dept, Integer grade, Integer term, String category, String campus, Mode mode, Data d) {
        // 검색어에 학년·학기·학과가 섞여 오면 칸으로 옮긴다(11-10 실제 모델: '컴공 1학년 과목'을 query 에 그대로 넣었다).
        String rest = query == null ? "" : query;
        boolean countQuestion = COUNT_QUESTION.matcher(rest).find();
        java.util.regex.Matcher g = java.util.regex.Pattern.compile("([1-5])\\s*학년").matcher(rest);
        if (g.find()) {
            if (grade == null) {
                grade = Integer.parseInt(g.group(1));
            }
            rest = rest.replace(g.group(), " ");
        }
        java.util.regex.Matcher t = java.util.regex.Pattern.compile("([12])\\s*학기").matcher(rest);
        if (t.find()) {
            if (term == null) {
                term = Integer.parseInt(t.group(1));
            }
            rest = rest.replace(t.group(), " ");
        }
        // 이수구분 낱말은 버리지 않고 필터로 옮긴다(예전에는 '전공필수'를 군말로 지웠다).
        String cat = normalizeCategory(category);
        if (category != null && !category.isBlank() && cat == null) {
            cat = firstCategoryWord(category);
        }
        java.util.regex.Matcher cw = CATEGORY_WORD.matcher(rest);
        if (cw.find()) {
            if (cat == null) {
                cat = normalizeCategory(cw.group());
            }
            rest = CATEGORY_WORD.matcher(rest).replaceAll(" ");
        }
        if (dept == null || dept.isBlank()) {
            Optional<AskBoards.Match> board = AskBoards.find(rest);
            if (board.isPresent()) {
                dept = board.get().name();
                rest = board.get().strip(rest);
            }
        } else {
            dept = AskBoards.find(dept).map(AskBoards.Match::name).orElse(dept);
        }
        rest = rest.replaceAll("(과목|교과과정|커리큘럼|수업|뭐|들어|듣는|들어야|목록|알려줘|있어)", " ").strip();
        rest = dropCountWords(rest);
        boolean noDept = dept == null || dept.isBlank();
        boolean noName = squash(rest).isEmpty();
        if (mode == Mode.COUNT_BY_DEPT || (noName && countQuestion) || (noDept && noName && (cat != null || grade != null))) {
            return countByDept(rest, dept, grade, term, cat, campus, d);
        }
        String result = searchOnce(rest, dept, grade, term, cat, campus, d);
        // 이름으로 못 찾았는데 학과는 있으면 그 학과(학년) 과목 목록으로.
        if (!result.startsWith(SOURCE_REF) && !squash(rest).isEmpty() && !noDept) {
            result = searchOnce("", dept, grade, term, cat, campus, d);
        }
        return result;
    }

    private static String firstCategoryWord(String text) {
        java.util.regex.Matcher m = CATEGORY_WORD.matcher(text);
        return m.find() ? normalizeCategory(m.group()) : null;
    }

    private static String dropCountWords(String text) {
        List<String> kept = new ArrayList<>();
        for (String token : text.split("[\\s,.?!~]+")) {
            if (token.isEmpty()) {
                continue;
            }
            String bare = JOSA_TAIL.matcher(token).replaceAll("");
            // '1학기에'에서 학기를 뺀 뒤 남는 '에' 같은 조사 조각도 버린다.
            if (!bare.isEmpty() && !COUNT_WORDS.contains(token) && !COUNT_WORDS.contains(bare)) {
                kept.add(token);
            }
        }
        return String.join(" ", kept);
    }

    private static Set<String> nameKeys(String query, Data d) {
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
        return names;
    }

    private static boolean nameMatches(Course c, String query, Set<String> names) {
        if (names.isEmpty() || squash(query).equals(c.code())) {
            return true;
        }
        String name = squash(c.name()).replaceAll("\\([^)]*\\)", "");
        return names.stream().anyMatch(n -> name.contains(n.replaceAll("\\([^)]*\\)", "")));
    }

    private static String conditions(String wantCampus, String dept, Integer grade, Integer term, String cat, String query) {
        List<String> parts = new ArrayList<>(List.of(wantCampus));
        if (dept != null && !dept.isBlank()) {
            parts.add(dept);
        }
        if (grade != null) {
            parts.add(grade + "학년");
        }
        if (term != null) {
            parts.add(term + "학기");
        }
        if (cat != null) {
            parts.add(cat);
        }
        if (query != null && !squash(query).isEmpty()) {
            parts.add("이름 '" + query.strip() + "'");
        }
        return String.join(" · ", parts);
    }

    private static double credits(String text) {
        double sum = 0;
        for (String x : (text == null ? "" : text).split(",")) {
            try {
                sum += Double.parseDouble(x.strip());
            } catch (NumberFormatException ignored) {
                // '-'·빈칸은 0
            }
        }
        return sum;
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** 조건에 맞는 과목을 모든 행에서 세어 학과별로(과목 수 많은 순). 같은 학년의 같은 과목이 두 학기에 다 있으면 한 번만 센다. */
    static String countByDept(String query, String dept, Integer grade, Integer term, String cat, String campus, Data d) {
        String wantCampus = campus != null && campus.contains("세종") ? "세종" : "서울";
        String stem = dept == null ? "" : deptStem(dept);
        Set<String> names = nameKeys(query, d);
        Map<String, Map<String, Course>> byDept = new LinkedHashMap<>();
        Set<String> deptsInScope = new LinkedHashSet<>();
        for (Course c : d.courses()) {
            if (!c.campus().equals(wantCampus) || (!stem.isEmpty() && !squash(c.dept()).contains(stem))
                    || (grade != null && c.grade() != grade) || (term != null && c.term() != term)) {
                continue;
            }
            deptsInScope.add(c.dept());
            if (categoryMatches(cat, c) && nameMatches(c, query, names)) {
                byDept.computeIfAbsent(c.dept(), k -> new LinkedHashMap<>()).putIfAbsent(c.grade() + "|" + c.name(), c);
            }
        }
        String head = SOURCE_REF + " · " + SOURCE_TITLE + " 기준 — 조건: " + conditions(wantCampus, dept, grade, term, cat, query)
                + ". 서버가 책자 전체 " + d.courses().size() + "행을 세었다(교육과정 기준, 이번 학기 개설은 수강신청 시스템).\n";
        if (byDept.isEmpty()) {
            return "2026 교과과정책자(" + wantCampus + ")에서 조건(" + conditions(wantCampus, dept, grade, term, cat, query)
                    + ")에 맞는 과목이 있는 학과가 없어요(살펴본 학과 " + deptsInScope.size() + "곳).";
        }
        record Row(String dept, List<Course> courses, double credits) {
        }
        List<Row> rows = new ArrayList<>();
        byDept.forEach((name, map) -> {
            List<Course> list = new ArrayList<>(map.values());
            list.sort(Comparator.comparingInt(Course::grade).thenComparingInt(Course::term).thenComparing(Course::name));
            rows.add(new Row(name, list, list.stream().mapToDouble(c -> credits(c.credits())).sum()));
        });
        rows.sort(Comparator.comparingInt((Row r) -> -r.courses().size()).thenComparingDouble(r -> -r.credits()).thenComparing(Row::dept));
        StringBuilder out = new StringBuilder(head);
        out.append("맞는 학과 ").append(rows.size()).append("곳 / 살펴본 학과 ").append(deptsInScope.size()).append("곳 (과목 수 많은 순)\n");
        int lines = 0;
        for (Row r : rows) {
            if (lines++ >= MAX_LINES) {
                out.append("(학과가 더 있어요 — ").append(rows.size() - MAX_LINES).append("곳 생략)\n");
                break;
            }
            List<String> sample = new ArrayList<>();
            for (Course c : r.courses()) {
                if (sample.size() >= 4) {
                    sample.add("…");
                    break;
                }
                sample.add((grade == null ? c.grade() + "학년 " : "") + (term == null ? c.term() + "학기 " : "") + c.name()
                        + " " + c.credits() + "학점");
            }
            boolean unchecked = r.courses().stream().anyMatch(c -> !c.checked());
            out.append("- ").append(r.dept()).append(": ").append(r.courses().size()).append("과목 · ").append(num(r.credits())).append("학점 — ")
                    .append(String.join(", ", sample)).append(unchecked ? " · 일부 책자 합계 검산 안 됨" : "").append('\n');
        }
        return out.toString();
    }

    private static String searchOnce(String query, String dept, Integer grade, Integer term, String cat, String campus, Data d) {
        Set<String> names = nameKeys(query, d);
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
                    || (term != null && c.term() != term)
                    || !categoryMatches(cat, c)) {
                continue;
            }
            if (nameMatches(c, query, names) && seen.add(c.dept() + c.grade() + c.term() + c.name())) {
                hits.add(c);
            }
        }
        if (hits.isEmpty()) {
            return "2026 교과과정책자(" + wantCampus + ")에서 맞는 과목을 찾지 못했어요"
                    + (cat == null ? "." : "(조건: " + conditions(wantCampus, dept, grade, term, cat, query) + ").");
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
                    .append(c.category() == null ? "" : " · " + c.category() + (c.categoryDetail() == null ? "" : "(" + c.categoryDetail() + ")"))
                    .append(c.checked() ? "" : " · 책자 합계 검산 안 됨(원문 " + c.src().replace("2026 교과과정책자 PDF ", "") + " 확인)").append('\n');
        }
        return out.toString();
    }
}
