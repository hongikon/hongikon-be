package com.hongmap.hongmapbackend.crawler;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 게시판이 카테고리를 주지 않으므로 제목 키워드로 추정한다.
 *
 * 결과 값 집합은 NotificationCategoryService.CATEGORIES(공지·장학·행사·수강·시설·취업·상담) 7종과 반드시 맞아야 한다 —
 * 여기서 값이 갈리면 알림 구독 필터링이 어긋난다.
 *
 * hongmap(프론트) scripts/crawler/classify.mjs를 기반으로 하되, 그쪽은 5종(장학·취업·수강·행사·상담)만 지원해
 * "시설"이 전부 기본값(공지)으로 흘러갔다 — 이 포팅에서는 시설 규칙을 추가했다.
 * 순서가 우선순위다: 앞 규칙에 먼저 걸리면 뒤는 보지 않는다.
 */
public final class NewsCategoryClassifier {

    private static final String DEFAULT_CATEGORY = "공지";

    private static final List<Map.Entry<String, Pattern>> RULES = List.of(
            Map.entry("장학", Pattern.compile("장학|등록금|학자금")),
            Map.entry("취업", Pattern.compile("취업|채용|인턴|기업|박람회|공고|연구원|모집")),
            Map.entry("수강", Pattern.compile("수강|성적|졸업|학점|교과|전공|시험|수업|계절학기|등록|휴학|복학")),
            Map.entry("행사", Pattern.compile("축제|행사|전시|공연|대회|특강|세미나|워크숍|해커톤|공모전")),
            Map.entry("시설", Pattern.compile("시설|공사|정전|단수|점검|보수|주차장|엘리베이터|소방|안전진단")),
            Map.entry("상담", Pattern.compile("상담|심리|건강|보건"))
    );

    private NewsCategoryClassifier() {
    }

    /** 제목에서 카테고리를 추정한다. 맞는 규칙이 없으면 "공지". */
    public static String classify(String title) {
        if (title == null) return DEFAULT_CATEGORY;

        return RULES.stream()
                .filter(rule -> rule.getValue().matcher(title).find())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(DEFAULT_CATEGORY);
    }
}
