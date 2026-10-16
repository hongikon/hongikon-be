package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.cafeteria.CafeteriaMenuService;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaDayResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaMealResponse;
import com.hongmap.hongmapbackend.cafeteria.dto.CafeteriaRestaurantResponse;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
     * 제목에 맞는 낱말이 많은 공지부터, 같으면 최신순으로. 낱말(2글자 이상, 최대 5개)마다 같은 뜻 낱말({@link #SYNONYMS})도 본다.
     * 하나도 안 맞으면 빈 목록.
     */
    @Transactional(readOnly = true)
    public List<Reference> searchNotices(String query) {
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
                    likes.add(cb.like(cb.lower(root.get("title")), "%" + escape(word.toLowerCase()) + "%", '!'));
                }
            }
            return cb.or(likes.toArray(Predicate[]::new));
        };
        List<News> candidates = newsRepository.findAll(anyWord,
                PageRequest.of(0, NOTICE_CANDIDATES, Sort.by(Sort.Direction.DESC, "publishedAt"))).getContent();
        return candidates.stream()
                .sorted(Comparator.comparingInt((News news) -> -matched(news.getTitle(), groups)))
                .limit(NOTICE_SEARCH_LIMIT)
                .map(AskLiveData::reference)
                .toList();
    }

    /** 제목에 맞은 낱말 묶음 수. */
    private static int matched(String title, List<List<String>> groups) {
        String lower = title.toLowerCase();
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

    /** 공지 본문(앞 {@link #NOTICE_BODY_MAX}자)과 참조. */
    @Transactional(readOnly = true)
    public Optional<NoticeBody> readNotice(long id) {
        return newsRepository.findById(id).map(news -> {
            String content = news.getContent() == null ? "" : news.getContent().replaceAll("<[^>]+>", " ").replaceAll("[ \\t]+", " ").trim();
            if (content.length() > NOTICE_BODY_MAX) {
                content = content.substring(0, NOTICE_BODY_MAX) + "\n(이하 생략)";
            }
            return new NoticeBody(reference(news), content);
        });
    }

    public record NoticeBody(Reference reference, String content) {
    }

    /** 그날 학식(날짜를 못 읽으면 오늘). 메뉴가 없으면 식당만 나온다. */
    public DayMenu cafeteria(String date) {
        LocalDate day;
        try {
            day = date == null || date.isBlank() || "today".equals(date) ? cafeteriaMenuService.today() : LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            day = cafeteriaMenuService.today();
        }
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
        return new DayMenu(reference, any ? text.toString() : text + "(이날 저장된 메뉴가 없어요)\n");
    }

    public record DayMenu(Reference reference, String text) {
    }

    private static Reference reference(News news) {
        return new Reference("notice:" + news.getId(), news.getTitle(), news.getSourceUrl(),
                news.getPublishedAt() == null ? null : news.getPublishedAt().toLocalDate().toString());
    }
}
