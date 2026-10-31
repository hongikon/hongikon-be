package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuService;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaDayResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaMealResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaRestaurantResponse;
import com.hongmap.hongmapbackend.mapdata.MapDataService;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 물어보기 답 쓰기에서 LLM 이 부르는 '지금 데이터' 도구 — 안내 원문(askPages)에는 "매 학기 공지 확인"으로만 적힌 날짜·마감을
 * 학교 공지(크롤러가 모은 news)에서 찾고, 학식은 서버가 가져와 둔 메뉴를 쓴다. 모두 학교가 공개한 글이라 개인정보는 없다.
 * 결과 글에는 근거로 쓸 수 있는 참조(`notice:{id}`, `cafeteria:{yyyy-MM-dd}`)를 붙인다.
 */
@Component
@RequiredArgsConstructor
public class AskLiveData {

    static final int NOTICE_SEARCH_LIMIT = 6;
    /** 공지 본문 최대 글자 수. 로컬 DB 기준 가장 긴 공지가 약 5,800자(10-16) — 수강신청 안내처럼 철회 일정이 뒤에 있는 글을 자르지 않게. */
    static final int NOTICE_BODY_MAX = 6_000;

    private final NewsRepository newsRepository;
    private final CafeteriaMenuService cafeteriaMenuService;
    private final MapDataService mapDataService;
    private final JsonMapper jsonMapper;

    static final int FACILITY_SEARCH_LIMIT = 8;

    /**
     * 지도 편의시설(GET /map/data 의 facilities — 건물·층·종류·메모) 낱말 검색. 공동학습실·프레젠테이션룸·학과 라운지처럼 학교 홈페이지 안내에는 없고
     * 운영진이 지도에 넣어 둔 운영시간·예약 방법(헤이영캠퍼스 공간예약 등)을 답에 쓰게 한다(10-16, 'K동 공동학습실'·'멀티미디어실'을 못 찾았다).
     * 맞은 낱말이 많은 순. 참조는 `facility:{id}`(앱이 위치 버튼으로 그린다).
     */
    public List<Reference> searchFacilities(String query, StringBuilder text) {
        List<String> words = queryWords(query);
        if (words.isEmpty()) {
            return List.of();
        }
        JsonNode facilities = jsonMapper.readTree(mapDataService.current().body()).path("facilities");
        record Hit(JsonNode node, String line, int score) {
        }
        List<Hit> hits = new ArrayList<>();
        for (JsonNode facility : facilities) {
            String line = facilityLine(facility);
            int score = score(line, words);
            if (score > 0) {
                hits.add(new Hit(facility, line, score));
            }
        }
        hits.sort((a, b) -> b.score() - a.score());
        List<Reference> references = new ArrayList<>();
        for (Hit hit : hits.subList(0, Math.min(FACILITY_SEARCH_LIMIT, hits.size()))) {
            String ref = "facility:" + hit.node().path("id").asString("");
            text.append(ref).append(" · ").append(hit.line()).append('\n');
            references.add(new Reference(ref, hit.line(), null, null));
        }
        return references;
    }

    static final int BUILDING_SEARCH_LIMIT = 3;
    /** 건물 하나에 붙일 시설 줄 수 상한(홍문관처럼 연구소·사무실이 40곳 넘는 건물도 있다). */
    static final int BUILDING_FACILITY_LINES = 60;
    static final int PARTNER_SEARCH_LIMIT = 8;

    /**
     * 지도 건물(GET /map/data 의 buildings) 낱말 검색 — 건물 이름·별칭(R동 …)·종류·소개·운영시간·층수에, 그 건물의 층별 시설과 지금 하는 전시까지
     * 한 번에 준다('홍문관에 뭐 있어', 'Q동 몇 층까지', '조형관 운영시간'). 이름이 맞은 건물이 먼저. 참조는 `building:{code}`.
     */
    public List<Reference> searchBuildings(String query, StringBuilder text) {
        List<String> words = queryWords(query);
        if (words.isEmpty()) {
            return List.of();
        }
        JsonNode data = jsonMapper.readTree(mapDataService.current().body());
        record Hit(JsonNode node, int score) {
        }
        List<Hit> hits = new ArrayList<>();
        for (JsonNode building : data.path("buildings")) {
            String name = building.path("name").asString("");
            String about = String.join(" ", name, building.path("code").asString(""), building.path("type").asString(""),
                    building.path("category").asString(""), building.path("description").asString(""));
            // 이름에 맞은 낱말은 두 배 — '홍문관 카페'에서 소개에 '카페'가 적힌 다른 건물보다 홍문관이 먼저.
            int score = score(name, words) * 2 + score(about, words);
            if (score > 0) {
                hits.add(new Hit(building, score));
            }
        }
        hits.sort((a, b) -> b.score() - a.score());
        LocalDate today = cafeteriaMenuService.today();
        List<Reference> references = new ArrayList<>();
        for (Hit hit : hits.subList(0, Math.min(BUILDING_SEARCH_LIMIT, hits.size()))) {
            JsonNode building = hit.node();
            String name = building.path("name").asString("");
            String ref = "building:" + building.path("code").asString("");
            text.append(ref).append(" · ").append(name).append('\n');
            appendField(text, "종류", building.path("type").asString(null));
            if (building.path("floors").isNumber() || building.path("basementFloors").isNumber()) {
                appendField(text, "층수", (building.path("basementFloors").isNumber() ? "지하 " + building.path("basementFloors").asInt() + "층 ~ " : "")
                        + (building.path("floors").isNumber() ? "지상 " + building.path("floors").asInt() + "층" : ""));
            }
            appendField(text, "운영시간", building.path("hours").asString(null));
            appendField(text, "소개", building.path("description").asString(null));
            if (!building.path("facilities").isMissingNode() && !building.path("facilities").isNull()) {
                appendField(text, "건물 시설", building.path("facilities").toString());
            }
            appendField(text, "안내 링크", building.path("link").path("label").asString(null));
            List<String> facilityIds = new ArrayList<>();
            int lines = 0;
            for (JsonNode facility : sortedByFloor(data.path("facilities"), name)) {
                if (lines++ == 0) {
                    text.append("- 층별 시설:\n");
                }
                if (lines > BUILDING_FACILITY_LINES) {
                    text.append("  (시설이 더 있어요 — search_facilities 로 찾는다)\n");
                    break;
                }
                facilityIds.add(facility.path("id").asString(""));
                text.append("  facility:").append(facility.path("id").asString("")).append(" · ").append(facilityLine(facility)).append('\n');
            }
            for (JsonNode exhibition : data.path("exhibitions")) {
                if (facilityIds.contains(exhibition.path("facilityId").asString("")) && runsOn(exhibition, today)) {
                    text.append("- 지금 전시: ").append(exhibition.path("title").asString("")).append(" (")
                            .append(exhibition.path("startsOn").asString("")).append(" ~ ").append(exhibition.path("endsOn").asString(""))
                            .append(exhibition.path("hours").isString() ? ", " + exhibition.path("hours").asString() : "").append(")\n");
                }
            }
            references.add(new Reference(ref, name, null, null));
        }
        return references;
    }

    /**
     * 학교 제휴업체(GET /map/data 의 partners) 낱말 검색 — 이름·종류·소속·혜택·주소. 제휴가 끝난 업체는 뺀다.
     * 전화번호는 싣지 않는다(답 본문에 쓰지 않는 규칙이고, 앱 지도에서 업체를 열면 보인다). 참조는 `partner:{id}`.
     */
    public List<Reference> searchPartners(String query, StringBuilder text) {
        List<String> words = queryWords(query);
        if (words.isEmpty()) {
            return List.of();
        }
        JsonNode partners = jsonMapper.readTree(mapDataService.current().body()).path("partners");
        LocalDate today = cafeteriaMenuService.today();
        record Hit(JsonNode node, String line, int score) {
        }
        List<Hit> hits = new ArrayList<>();
        for (JsonNode partner : partners) {
            String endsOn = partner.path("endsOn").asString("");
            if (!endsOn.isEmpty() && endsOn.compareTo(today.toString()) < 0) {
                continue;
            }
            StringBuilder line = new StringBuilder(partner.path("name").asString(""))
                    .append(" [").append(partner.path("category").asString("")).append("]");
            List<String> affiliations = new ArrayList<>();
            partner.path("affiliations").forEach(affiliation -> affiliations.add(affiliation.asString("")));
            if (!affiliations.isEmpty()) {
                line.append(" · 대상: ").append(String.join(", ", affiliations));
            }
            appendPart(line, "혜택", partner.path("benefit").asString(null));
            for (JsonNode special : partner.path("affiliationBenefits")) {
                appendPart(line, special.path("affiliation").asString("") + " 혜택", special.path("benefit").asString(null));
            }
            appendPart(line, "주소", partner.path("address").asString(null));
            appendPart(line, "시간", partner.path("hours").asString(null));
            appendPart(line, "이용 방법", partner.path("usageNote").asString(null));
            appendPart(line, "제휴 종료", endsOn.isEmpty() ? null : endsOn + "까지");
            int score = score(line.toString(), words);
            if (score > 0) {
                hits.add(new Hit(partner, line.toString(), score));
            }
        }
        hits.sort((a, b) -> b.score() - a.score());
        List<Reference> references = new ArrayList<>();
        for (Hit hit : hits.subList(0, Math.min(PARTNER_SEARCH_LIMIT, hits.size()))) {
            String ref = "partner:" + hit.node().path("id").asString("");
            text.append(ref).append(" · ").append(hit.line()).append('\n');
            references.add(new Reference(ref, hit.node().path("name").asString(""), null, null));
        }
        return references;
    }

    /** 질문에서 2글자 이상 낱말 최대 6개(소문자). */
    private static List<String> queryWords(String query) {
        List<String> words = new ArrayList<>();
        for (String word : query.split("[\\s,·/()?!.]+")) {
            if (word.length() >= 2 && words.size() < 6) {
                words.add(word.toLowerCase());
            }
        }
        return words;
    }

    private static int score(String text, List<String> words) {
        String lower = text.toLowerCase();
        int score = 0;
        for (String word : words) {
            if (lower.contains(word)) {
                score++;
            }
        }
        return score;
    }

    private static String facilityLine(JsonNode facility) {
        String floor = facility.path("floor").isNumber() ? floorLabel(facility.path("floor").asInt()) : "";
        return facility.path("buildingName").asString("") + " " + floor + " · [" + facility.path("kind").asString("") + "] "
                + facility.path("note").asString("");
    }

    /** 그 건물 시설을 위층부터(층 모르는 것은 맨 뒤). */
    private static List<JsonNode> sortedByFloor(JsonNode facilities, String buildingName) {
        List<JsonNode> inBuilding = new ArrayList<>();
        for (JsonNode facility : facilities) {
            if (buildingName.equals(facility.path("buildingName").asString(""))) {
                inBuilding.add(facility);
            }
        }
        inBuilding.sort(Comparator.comparingInt((JsonNode facility) -> facility.path("floor").isNumber() ? -facility.path("floor").asInt() : Integer.MAX_VALUE));
        return inBuilding;
    }

    private static boolean runsOn(JsonNode exhibition, LocalDate day) {
        String startsOn = exhibition.path("startsOn").asString("");
        String endsOn = exhibition.path("endsOn").asString("");
        return (startsOn.isEmpty() || startsOn.compareTo(day.toString()) <= 0) && (endsOn.isEmpty() || endsOn.compareTo(day.toString()) >= 0);
    }

    private static void appendField(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append("- ").append(label).append(": ").append(value).append('\n');
        }
    }

    private static void appendPart(StringBuilder line, String label, String value) {
        if (value != null && !value.isBlank()) {
            line.append(" · ").append(label).append(": ").append(value);
        }
    }

    /** 근거로 붙일 바깥 자료(공지·학식). */
    public record Reference(String ref, String title, String url, String date) {
    }

    /**
     * 학생 말과 공지 제목 말이 다른 경우 — 학생은 '교환학생', 공지는 '교류학생'·'Outbound'라고 쓴다(10-16, 교환학생 모집 공고를 못 찾았다).
     * 검색 낱말마다 같은 뜻 낱말도 함께 본다(하나라도 제목에 있으면 그 낱말은 맞은 것).
     */
    private static final Map<String, List<String>> SYNONYMS = Map.of(
            "교환학생", List.of("교류학생", "Outbound", "파견"),
            "교환", List.of("교류", "Outbound", "파견"),
            "파견", List.of("Outbound", "교류"),
            "장학금", List.of("장학"),
            "학식", List.of("식당", "식단"),
            "자퇴", List.of("자퇴원"),
            "졸업요건", List.of("졸업", "졸업학점"));

    /** 후보로 보는 공지 수(낱말 하나라도 맞은 최신 글). 그중 맞은 낱말 수 → 최신순으로 고른다. */
    static final int NOTICE_CANDIDATES = 300;

    /**
     * 맞는 낱말이 많은 공지부터, 같으면 최신순으로. 낱말(2글자 이상, 최대 5개)마다 같은 뜻 낱말({@link #SYNONYMS})도 본다.
     * 제목에 맞으면 3점, 본문에만 맞으면 1점 — 본문에만 있는 사실(Q&A 공지의 '학점 이월 최대 2학점' 같은 것)도 찾는다(10-25 운영진 요청).
     * 하나도 안 맞으면 빈 목록.
     */
    @Transactional(readOnly = true)
    public List<NoticeHit> searchNotices(String query) {
        return searchNotices(query, null);
    }

    /** {@link #searchNotices(String)} 를 한 게시판(news.source_id — 학과·기관 이름)으로 좁힌다. board 가 비면 전체. */
    @Transactional(readOnly = true)
    public List<NoticeHit> searchNotices(String query, String board) {
        List<List<String>> groups = new ArrayList<>();
        for (String word : query.split("[\\s,·/()?!.]+")) {
            if (word.length() >= 2 && groups.size() < 5) {
                List<String> group = new ArrayList<>(List.of(word));
                group.addAll(SYNONYMS.getOrDefault(word, List.of()));
                groups.add(group);
            }
        }
        if (groups.isEmpty()) {
            return List.of();
        }
        Specification<News> anyWord = (root, cq, cb) -> {
            List<Predicate> likes = new ArrayList<>();
            for (List<String> group : groups) {
                for (String word : group) {
                    String pattern = "%" + escape(word.toLowerCase()) + "%";
                    likes.add(cb.like(withoutDots(cb, root.get("title")), pattern, '!'));
                    likes.add(cb.like(withoutDots(cb, root.get("content")), pattern, '!'));
                }
            }
            Predicate any = cb.or(likes.toArray(Predicate[]::new));
            return board == null || board.isBlank() ? any : cb.and(any, cb.equal(root.get("sourceId"), board));
        };
        List<News> candidates = newsRepository.findAll(anyWord,
                PageRequest.of(0, NOTICE_CANDIDATES, Sort.by(Sort.Direction.DESC, "publishedAt"))).getContent();
        return candidates.stream()
                .sorted(Comparator.comparingInt((News news) -> -(3 * matched(news.getTitle(), groups) + matched(news.getContent(), groups))))
                .limit(NOTICE_SEARCH_LIMIT)
                .map(news -> new NoticeHit(reference(news), news.getSourceId()))
                .toList();
    }

    /** 공지 찾기 결과 하나. board 는 수집한 게시판(학사·장학 같은 대학공지 분류, 또는 학과 이름) — 학과 공지를 학교 전체 기준처럼 쓰지 않게 함께 넘긴다. */
    public record NoticeHit(Reference reference, String board) {
    }

    /** 가운뎃점(‧ · ・)을 지우고 소문자로 — '학‧예술상' 공지가 '학예술상'으로 찾히게. */
    private static Expression<String> withoutDots(CriteriaBuilder cb, Expression<String> text) {
        Expression<String> out = cb.lower(text);
        for (String dot : DOTS) {
            out = cb.function("replace", String.class, out, cb.literal(dot), cb.literal(""));
        }
        return out;
    }

    private static final List<String> DOTS = List.of("‧", "·", "・");

    /** 글(제목·본문)에 맞은 낱말 묶음 수. */
    private static int matched(String text, List<List<String>> groups) {
        if (text == null) {
            return 0;
        }
        String plain = text.toLowerCase();
        for (String dot : DOTS) {
            plain = plain.replace(dot, "");
        }
        String lower = plain;
        int count = 0;
        for (List<String> group : groups) {
            if (group.stream().anyMatch(word -> lower.contains(word.toLowerCase()))) {
                count++;
            }
        }
        return count;
    }

    private static String escape(String word) {
        return word.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** 지도 데이터 층 값 → 답에 쓰는 말. 0 은 B1 과 1층 사이의 로비층(홍문관 R동 L층). */
    static String floorLabel(int floor) {
        if (floor == 0) {
            return "로비층(L층)";
        }
        return floor < 0 ? "지하 " + (-floor) + "층" : floor + "층";
    }

    /** 공지 본문(앞 {@link #NOTICE_BODY_MAX}자)과 참조. */
    @Transactional(readOnly = true)
    public Optional<NoticeBody> readNotice(long id) {
        return newsRepository.findById(id).map(news -> {
            String content = news.getContent() == null ? "" : news.getContent().replaceAll("<[^>]+>", " ").replaceAll("[ \\t]+", " ").trim();
            if (content.length() > NOTICE_BODY_MAX) {
                content = content.substring(0, NOTICE_BODY_MAX) + "\n(이하 생략)";
            }
            return new NoticeBody(reference(news), news.getSourceId(), content);
        });
    }

    public record NoticeBody(Reference reference, String board, String content) {
    }

    /** 그날 메뉴가 없을 때(주말·휴일) 다음 메뉴가 있는 날을 찾아보는 날 수. */
    static final int NEXT_MENU_DAYS = 7;

    /** 그날 학식(날짜를 못 읽으면 오늘). 그날 메뉴가 없으면 다음 메뉴가 있는 날을 붙인다. */
    public DayMenu cafeteria(String date) {
        LocalDate day;
        try {
            day = date == null || date.isBlank() || "today".equals(date) ? cafeteriaMenuService.today() : LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            day = cafeteriaMenuService.today();
        }
        return cafeteria(day, true);
    }

    /**
     * 그날 학식. lookAhead 면 그날 메뉴가 없을 때 {@link #NEXT_MENU_DAYS}일 안의 다음 메뉴가 있는 날을 붙인다 —
     * 토요일에 '오늘 학식'을 물으면 '오늘은 없음'만 말하고 끝나지 않게(10-31). 이번 주 전체를 볼 때는 끈다(날마다 이미 다 본다).
     */
    public DayMenu cafeteria(LocalDate day, boolean lookAhead) {
        DayMenu menu = menuOn(day);
        if (menu.served() || !lookAhead) {
            return menu;
        }
        for (int i = 1; i <= NEXT_MENU_DAYS; i++) {
            DayMenu next = menuOn(day.plusDays(i));
            if (next.served()) {
                return new DayMenu(menu.reference(), menu.text() + "\n[다음 메뉴가 있는 날] " + dayLabel(next.reference().date())
                        + "\n" + next.text(), false);
            }
        }
        return menu;
    }

    /** '2026-10-31(토)'. */
    static String dayLabel(String date) {
        LocalDate day = LocalDate.parse(date);
        return date + "(" + day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }

    private DayMenu menuOn(LocalDate day) {
        CafeteriaDayResponse response = cafeteriaMenuService.day(day);
        StringBuilder text = new StringBuilder();
        boolean any = false;
        for (CafeteriaRestaurantResponse restaurant : response.restaurants()) {
            text.append("[").append(restaurant.name()).append("]\n");
            if (restaurant.meals().isEmpty()) {
                text.append("- 이날 메뉴 없음\n");
            }
            for (CafeteriaMealResponse meal : restaurant.meals()) {
                any = true;
                text.append("- ").append(meal.meal());
                if (meal.time() != null) {
                    text.append(' ').append(meal.time());
                }
                if (meal.price() != null) {
                    text.append(' ').append(meal.price());
                }
                text.append(meal.closed() ? " (운영 안 함: " : ": ").append(String.join(", ", meal.items())).append(meal.closed() ? ")" : "").append('\n');
            }
        }
        Reference reference = new Reference("cafeteria:" + day, "학식 " + day, response.sourceUrl(), day.toString());
        return new DayMenu(reference, any ? text.toString() : text + "(이날 저장된 메뉴가 없어요)\n", any);
    }

    /** served: 그날 끼니가 하나라도 저장돼 있나(다음 날을 붙였으면 그날 기준으로 false). */
    public record DayMenu(Reference reference, String text, boolean served) {
    }

    private static Reference reference(News news) {
        return new Reference("notice:" + news.getId(), news.getTitle(), news.getSourceUrl(),
                news.getPublishedAt() == null ? null : news.getPublishedAt().toLocalDate().toString());
    }
}
