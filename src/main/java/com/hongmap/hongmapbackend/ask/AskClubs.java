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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 중앙동아리 66개(11-12). 데이터는 FE `scripts/ask/clubs/crawl.py` 가 총학생회 사이트(hongikunion.com/clubs/central)의 공개 JSON 에서 만든
 * `ask/clubs.json` — 이름·별칭·분과·소개·활동·대상·동방·모집 안내·정기 모임·링크. 회장 이름 같은 개인정보는 없다.
 * 처음 쓸 때 한 번 읽는다(없으면 빈 데이터 — 물어보기는 그대로 돈다). 학교 홈페이지 옛 동아리 안내(sources.json clubs/…)보다
 * 새 자료라 동방·모임·링크는 이쪽을 따른다(답 쓰기 지시).
 */
final class AskClubs {

    private static final Logger log = LoggerFactory.getLogger(AskClubs.class);
    static final String SOURCE_REF = "clubs:hongikunion";
    static final String SOURCE_TITLE = "총학생회 중앙동아리 안내";
    static final String SOURCE_URL = "https://hongikunion.com/clubs/central";
    static final String CLUB_PREFIX = "club:";
    static final List<String> CATEGORIES = List.of("공연분과", "스포츠분과", "레저분과", "학술분과", "사회분과", "전시분과", "종교분과");
    /** 한 번에 자세히(동방·모임·모집·링크까지) 보이는 동아리 수. 더 많으면 한 줄씩 후보로. */
    static final int MAX_DETAIL = 4;
    static final int MAX_CANDIDATES = 15;
    private static final String JOSA = "(?:은|는|이|가|을|를|에|에서|도|랑|이랑|하고|의|으로|로|만|이야|야|이에요|예요|은요|는요|부터|까지|)";
    /** 동아리 이름 뒤에 붙는 말 — '뚜라미동방', '합창반은', 'HIUC동아리'. */
    private static final Pattern NAME_TAIL = Pattern.compile("^(?:동아리방|동아리|동방)?" + JOSA + "$");
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s,.?!~·/()\"'’]+");
    /** 이름을 낱말로 쪼갤 때 버리는 흔한 말('가톨릭 학생회 가듐'의 '학생회', '영원한 미소'의 '미소'). */
    private static final Set<String> NAME_STOP = Set.of("홍익대학교", "홍익", "학생회", "영원한", "미소", "in", "hiu", "중앙", "동아리", "hongik");
    /** 분야 검색에서 버리는 말 — 질문 틀('뭐 있어', '어디야')과 동아리 일반어. */
    private static final Set<String> FILLER = Set.of("동아리", "중앙동아리", "중동", "동방", "동아리방", "어디", "어디야", "어디에", "위치", "뭐", "뭐야",
            "뭐가", "뭐있어", "있어", "있나", "있어요", "있나요", "있니", "알려줘", "알려주세요", "목록", "리스트", "전부", "전체", "모두", "다", "언제",
            "모여", "모임", "정기모임", "모집", "모집해", "모집해요", "모집기간", "가입", "가입해", "가입하려면", "지원", "인스타", "인스타그램", "링크",
            "유튜브", "홈페이지", "소개", "추천", "추천해줘", "홍대", "홍익대", "홍익대학교", "중앙", "하는", "관련", "같은", "좋은", "들어가고", "싶어",
            "시간", "몇시", "어떤", "무슨", "종류", "분과", "그", "거기", "좀", "해", "하고", "들", "곳", "데", "중에", "있는", "동아리는", "동아리가");
    /**
     * 분야 낱말 → 동아리 소개에 실제로 쓰인 말. 질문마다가 아니라 분야 낱말마다 한 줄 — 소개·활동 글에서 찾는다
     * ('춤' 동아리 소개에는 '댄스'·'스트릿'이, '밴드'를 묻는 학생에게는 '악기'·'합주' 동아리도 후보다).
     */
    private static final Map<String, List<String>> TOPIC = Map.ofEntries(
            Map.entry("밴드", List.of("밴드", "악기", "합주")),
            Map.entry("춤", List.of("춤", "댄스", "스트릿")),
            Map.entry("댄스", List.of("춤", "댄스", "스트릿")),
            Map.entry("노래", List.of("노래", "합창", "보컬")),
            Map.entry("음악", List.of("음악", "악기")),
            Map.entry("축구", List.of("축구", "풋살")),
            Map.entry("그림", List.of("그림", "미술", "드로잉")),
            Map.entry("미술", List.of("그림", "미술", "드로잉")),
            Map.entry("코딩", List.of("코딩", "프로그래밍", "개발", "아두이노")),
            Map.entry("프로그래밍", List.of("코딩", "프로그래밍", "개발")),
            Map.entry("개발", List.of("코딩", "프로그래밍", "개발")),
            Map.entry("책", List.of("독서", "문학", "책")),
            Map.entry("독서", List.of("독서", "문학", "책")),
            Map.entry("연기", List.of("연극", "뮤지컬", "연기")),
            Map.entry("자전거", List.of("자전거", "바이크", "라이딩")),
            Map.entry("주식", List.of("투자", "주식", "금융", "리서치")),
            Map.entry("투자", List.of("투자", "주식", "금융", "리서치")),
            Map.entry("클라이밍", List.of("클라이밍", "암벽")),
            Map.entry("기독교", List.of("기독", "성경", "선교")),
            Map.entry("교회", List.of("기독", "성경", "선교")),
            Map.entry("성당", List.of("가톨릭", "성당")),
            Map.entry("천주교", List.of("가톨릭", "성당")));

    record Link(String type, String url) {
    }

    record Club(int id, String name, List<String> aliases, String category, String intro, String description,
                List<String> activities, List<String> targets, String room, String recruiting, String schedule,
                List<Link> links, String detailUrl) {

        String ref() {
            return CLUB_PREFIX + id;
        }

        /** 찾을 때 쓰는 글 — 이름·소개·활동·대상(소문자). */
        String text() {
            return (name + " " + String.join(" ", aliases) + " " + intro + " " + description + " "
                    + String.join(" ", activities) + " " + String.join(" ", targets)).toLowerCase();
        }
    }

    record Data(List<Club> clubs, String fetchedAt) {
        static final Data EMPTY = new Data(List.of(), "");
    }

    /** 찾은 결과. shown: 자세히 보인 동아리(참조 club:id 를 붙인다). found: 맞는 동아리가 있었다. */
    record Result(String text, List<Club> shown, boolean found) {
    }

    private static volatile Data data;

    private AskClubs() {
    }

    static Data data() {
        Data loaded = data;
        if (loaded == null) {
            synchronized (AskClubs.class) {
                if (data == null) {
                    data = load(JsonMapper.builder().build());
                }
                loaded = data;
            }
        }
        return loaded;
    }

    static Data load(JsonMapper mapper) {
        try (InputStream in = new ClassPathResource("ask/clubs.json").getInputStream()) {
            JsonNode root = mapper.readTree(in);
            List<Club> list = new ArrayList<>();
            for (JsonNode c : root.path("clubs")) {
                List<Link> links = new ArrayList<>();
                c.path("links").forEach(l -> links.add(new Link(l.path("type").asString(""), l.path("url").asString(""))));
                list.add(new Club(c.path("id").asInt(), c.path("name").asString(""), strings(c.path("aliases")),
                        c.path("category").asString(""), c.path("one_line_intro").asString(""), c.path("description").asString(""),
                        strings(c.path("activities")), strings(c.path("targets")), c.path("room").asString(""),
                        c.path("recruiting").asString(""), c.path("meeting_schedule").asString(""), List.copyOf(links),
                        c.path("detail_url").asString(SOURCE_URL)));
            }
            return new Data(List.copyOf(list), root.path("fetched_at").asString("").replaceAll("T.*", ""));
        } catch (Exception e) {
            log.warn("물어보기 중앙동아리 자료(ask/clubs.json)를 읽지 못해 끈다: {}", e.toString());
            return Data.EMPTY;
        }
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> {
            if (n.isString() && !n.asString().isBlank()) {
                out.add(n.asString().strip());
            }
        });
        return List.copyOf(out);
    }

    static String squash(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * 동아리를 부르는 이름들(소문자). 정식 이름, 괄호 앞·안('HIUC(합창반)' → hiuc·합창반), 별칭, 여러 낱말 이름이면 흔한 말을 뺀 낱말
     * ('고전기타반 네페르' → 네페르).
     */
    static Set<String> names(Club club) {
        Set<String> out = new LinkedHashSet<>();
        out.add(squash(club.name()));
        String base = club.name().replaceAll("\\(.*\\)", "").strip();
        out.add(squash(base));
        for (String alias : club.aliases()) {
            out.add(squash(alias));
        }
        String[] words = base.split("\\s+");
        if (words.length > 1) {
            for (String word : words) {
                out.add(squash(word));
            }
        }
        out.removeIf(n -> n.length() < 2 || NAME_STOP.contains(n));
        return out;
    }

    /** 질문 속에 이름이 나온 동아리(질문 순서 아님, 데이터 순서). 낱말 앞부분이 이름이고 나머지가 조사·'동방'일 때만. */
    static List<Club> named(String text, Data d) {
        List<Club> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String all = squash(text);
        List<String> tokens = new ArrayList<>();
        for (String token : TOKEN_SPLIT.split(text.toLowerCase())) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        for (Club club : d.clubs()) {
            boolean hit = false;
            for (String name : names(club)) {
                // 띄어 쓴 긴 이름('영원한 미소', 'AIESEC in HIU')은 붙여 쓴 질문 전체에서.
                if (name.length() >= 5 && all.contains(name)) {
                    hit = true;
                }
                for (String token : tokens) {
                    if (token.startsWith(name) && NAME_TAIL.matcher(token.substring(name.length())).matches()) {
                        hit = true;
                    }
                }
                if (hit) {
                    break;
                }
            }
            if (hit) {
                out.add(club);
            }
        }
        return out;
    }

    /** 질문의 분과('공연 동아리', '스포츠분과') — 정식 분과 이름 또는 null. */
    static String category(String text) {
        if (text == null) {
            return null;
        }
        String s = squash(text);
        for (String category : CATEGORIES) {
            String stem = category.replace("분과", "");
            if (s.contains(category) || s.matches(".*" + stem + "(동아리|분야|쪽|$).*")) {
                return category;
            }
        }
        if (s.contains("체육동아리")) {
            return "스포츠분과";
        }
        return null;
    }

    /** 분류·답 쓰기에 붙일 한 줄 — 질문 속 동아리 이름을 알린다(모델이 '뚜라미'를 모를 수 있다). */
    static String hint(String question) {
        List<Club> found = named(question, data());
        if (found.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Club club : found.subList(0, Math.min(MAX_DETAIL, found.size()))) {
            parts.add(club.name() + "(" + club.category() + ")");
        }
        return "[질문 속 동아리] 홍익대 중앙동아리 " + String.join(", ", parts)
                + " — 학생회·동아리 질문(school)이다. 답은 search_clubs 로 찾는다.\n";
    }

    /**
     * 동방 호실 → 건물까지. G323-6호 → 학생회관(G동) 3층 323-6호, B307-1호 → 제2기숙사 지하층 307-1호,
     * D413호 → 인문사회관(D동) 4층 413호(학교 홈페이지 옛 안내의 표기와 같다). 다른 꼴은 그대로.
     */
    static String roomLabel(String room) {
        if (room == null || room.isBlank()) {
            return "안내에 없음";
        }
        Matcher m = Pattern.compile("^([GBD])(\\d)(\\d{2}(?:-\\d+)?)호?$").matcher(room.strip());
        if (!m.matches()) {
            return room.strip();
        }
        String number = m.group(2) + m.group(3) + "호";
        return switch (m.group(1)) {
            case "G" -> room.strip() + "(학생회관 G동 " + m.group(2) + "층 " + number + ")";
            case "B" -> room.strip() + "(제2기숙사 지하층 " + number + ")";
            default -> room.strip() + "(인문사회관 D동 " + m.group(2) + "층 " + number + ")";
        };
    }

    /** 동방 건물의 지도 검색어(search_buildings 와 같은 낱말) — 모르면 null. */
    static String building(String room) {
        if (room == null) {
            return null;
        }
        String r = room.strip();
        if (r.startsWith("G") || r.contains("학생회관")) {
            return "학생회관";
        }
        if (r.matches("^B\\d.*") || r.contains("기숙사")) {
            return "제2기숙사";
        }
        if (r.startsWith("D")) {
            return "인문사회관 D동";
        }
        return null;
    }

    /** 분야 낱말 — 질문 틀·동아리 일반어·분과 이름을 빼고 조사를 뗀 것. */
    static List<String> topicWords(String query) {
        List<String> out = new ArrayList<>();
        for (String raw : TOKEN_SPLIT.split(query == null ? "" : query.toLowerCase())) {
            String word = raw.replaceAll("(동아리방|동아리|동방)" + JOSA + "$", "");
            if (!TOPIC.containsKey(word)) {
                word = word.replaceAll("(이랑|에서|으로|은|는|이|가|을|를|에|도|랑|의|로|만)$", "");
            }
            if (word.isEmpty() || FILLER.contains(word) || FILLER.contains(raw) || category(word) != null) {
                continue;
            }
            // 한 글자는 분야 낱말('춤'·'책')일 때만 — '뭐'·'좀' 같은 말이 소개 글 여기저기에 맞지 않게.
            if (word.length() < 2 && !TOPIC.containsKey(word)) {
                continue;
            }
            if (!out.contains(word)) {
                out.add(word);
            }
        }
        return out;
    }

    /**
     * 동아리 찾기. query: 동아리 이름·분야 낱말(밴드·축구·봉사 …)·비우면 목록. category: 분과(공연·스포츠·레저·학술·사회·전시·종교, 없으면 null).
     * 이름이 나오면 그 동아리를 자세히, 분야 낱말이면 소개·활동에 그 말이 든 동아리를(많이 맞은 순), 아무것도 없으면 분과별 목록.
     */
    static Result search(String query, String category, Data d) {
        if (d.clubs().isEmpty()) {
            return new Result("중앙동아리 자료를 읽지 못했어요.", List.of(), false);
        }
        String q = query == null ? "" : query;
        String cat = category(category);
        if (cat == null) {
            cat = category(q);
        }
        List<Club> named = named(q, d);
        if (!named.isEmpty()) {
            List<Club> shown = named.subList(0, Math.min(MAX_DETAIL, named.size()));
            return new Result(header(d) + details(shown), List.copyOf(shown), true);
        }
        List<String> words = topicWords(q);
        if (words.isEmpty()) {
            return new Result(header(d) + list(d, cat), List.of(), true);
        }
        record Hit(Club club, int score) {
        }
        List<Hit> hits = new ArrayList<>();
        List<String> matchedWords = new ArrayList<>();
        for (Club club : d.clubs()) {
            if (cat != null && !club.category().equals(cat)) {
                continue;
            }
            String head = (club.name() + " " + String.join(" ", club.aliases()) + " " + club.intro()).toLowerCase();
            String all = club.text();
            int score = 0;
            for (String word : words) {
                int best = 0;
                for (String term : TOPIC.getOrDefault(word, List.of(word))) {
                    best = Math.max(best, head.contains(term) ? 3 : all.contains(term) ? 1 : 0);
                }
                if (best > 0 && !matchedWords.contains(word)) {
                    matchedWords.add(word);
                }
                score += best;
            }
            if (score > 0) {
                hits.add(new Hit(club, score));
            }
        }
        if (hits.isEmpty()) {
            String scope = cat == null ? "중앙동아리 " + d.clubs().size() + "개" : cat;
            return new Result("총학생회 중앙동아리 안내(" + scope + ")에서 '" + String.join(" ", words)
                    + "'에 맞는 동아리를 찾지 못했어요. 중앙동아리 목록에 없는 이름이면 가동아리·학과 소모임·연합동아리일 수 있다"
                    + " — 그렇게 알린다. 학교 홈페이지 옛 동아리 안내에 그 이름이 있어도 옛 동방 호실은 지금 다른 동아리가 쓸 수 있으니 알려 주지 않는다."
                    + " 분과(" + String.join("·", CATEGORIES.stream().map(c -> c.replace("분과", "")).toList())
                    + ")나 다른 낱말로 다시 찾을 수 있다.", List.of(), false);
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed());
        List<Club> clubs = hits.stream().map(Hit::club).toList();
        if (clubs.size() <= 3) {
            return new Result(header(d) + "'" + String.join(" ", matchedWords) + "' 낱말이 소개에 든 동아리:\n" + details(clubs),
                    List.copyOf(clubs), true);
        }
        StringBuilder out = new StringBuilder(header(d)).append("'").append(String.join(" ", matchedWords))
                .append("' 낱말이 소개·활동에 든 후보(많이 맞은 순 — 소개를 읽고 질문 분야에 맞는 것만 답한다):\n");
        for (Club club : clubs.subList(0, Math.min(MAX_CANDIDATES, clubs.size()))) {
            out.append(line(club)).append('\n');
        }
        if (clubs.size() > MAX_CANDIDATES) {
            out.append("(후보가 더 있어요 — 분과나 다른 낱말로 좁힌다)\n");
        }
        return new Result(out.toString(), List.of(), true);
    }

    private static String header(Data d) {
        return SOURCE_REF + " · " + SOURCE_TITLE + "(" + SOURCE_URL + ", " + d.fetchedAt() + " 수집, 중앙동아리 " + d.clubs().size()
                + "개) — 동아리 이름·분과·동방·모임·모집 안내·링크는 이 결과가 기준이다. "
                + "'모집 안내'는 동아리가 적어 둔 문구라 지금 모집 중인지는 알 수 없다.\n";
    }

    /** 분과별 목록(분과를 주면 그 분과만). 한 줄: 이름 · 한 줄 소개 · 동방 · 모집 안내. */
    private static String list(Data d, String cat) {
        Map<String, List<Club>> byCategory = new LinkedHashMap<>();
        CATEGORIES.forEach(c -> byCategory.put(c, new ArrayList<>()));
        for (Club club : d.clubs()) {
            byCategory.computeIfAbsent(club.category(), k -> new ArrayList<>()).add(club);
        }
        StringBuilder out = new StringBuilder(cat == null ? "분과별 전체 목록:\n" : cat + " 목록:\n");
        byCategory.forEach((category, clubs) -> {
            if (clubs.isEmpty() || (cat != null && !cat.equals(category))) {
                return;
            }
            out.append("[").append(category).append(" ").append(clubs.size()).append("개]\n");
            clubs.forEach(club -> out.append(line(club)).append('\n'));
        });
        return out.toString();
    }

    private static String line(Club club) {
        return "- " + club.name() + " · " + club.category() + " · " + cut(club.intro(), 50)
                + " · 동방 " + club.room() + " · 모집 " + cut(club.recruiting(), 30) + " [참조 " + club.ref() + "]";
    }

    private static String details(List<Club> clubs) {
        StringBuilder out = new StringBuilder();
        for (Club club : clubs) {
            out.append("## ").append(club.name()).append(" · ").append(club.category()).append(" [참조 ").append(club.ref()).append("]\n");
            out.append("- 소개: ").append(club.intro()).append('\n');
            if (!club.description().isBlank()) {
                out.append("- 설명: ").append(cut(club.description(), 300)).append('\n');
            }
            if (!club.activities().isEmpty()) {
                out.append("- 활동: ").append(cut(String.join(" / ", club.activities()), 300)).append('\n');
            }
            if (!club.targets().isEmpty()) {
                out.append("- 이런 분 환영: ").append(cut(String.join(" / ", club.targets()), 200)).append('\n');
            }
            out.append("- 동방: ").append(roomLabel(club.room())).append('\n');
            out.append("- 정기 모임: ").append(club.schedule().isBlank() ? "안내에 없음" : club.schedule()).append('\n');
            out.append("- 모집 안내(동아리가 적은 문구): ").append(club.recruiting().isBlank() ? "안내에 없음" : club.recruiting()).append('\n');
            if (club.links().isEmpty()) {
                out.append("- 링크: 안내에 없음\n");
            } else {
                for (int i = 0; i < club.links().size(); i++) {
                    Link link = club.links().get(i);
                    out.append("- ").append(linkLabel(link.type())).append(": ").append(link.url())
                            .append(" [참조 ").append(linkRef(club, i)).append("]\n");
                }
            }
            out.append("- 총학생회 소개 쪽: ").append(club.detailUrl()).append('\n');
        }
        return out.toString();
    }

    /** 동아리 링크 하나의 참조 — 'club:9:link0'. 답 글에는 주소를 쓰지 않고(답 규칙 6) 이 참조로 앱이 링크 버튼을 붙인다. */
    static String linkRef(Club club, int index) {
        return club.ref() + ":link" + index;
    }

    /** 링크 버튼 제목 — 'HIUC(합창반) 인스타그램'. */
    static String linkTitle(Club club, int index) {
        return club.name() + " " + linkLabel(club.links().get(index).type());
    }

    private static String linkLabel(String type) {
        return switch (type) {
            case "instagram" -> "인스타그램";
            case "youtube" -> "유튜브";
            case "naver_blog" -> "네이버 블로그";
            case "naver_cafe" -> "네이버 카페";
            case "tistory" -> "티스토리";
            case "google_play" -> "구글 플레이";
            default -> "홈페이지";
        };
    }

    private static String cut(String s, int max) {
        String one = s == null ? "" : s.replaceAll("\\s+", " ").strip();
        return one.length() > max ? one.substring(0, max) + "…" : one;
    }
}
