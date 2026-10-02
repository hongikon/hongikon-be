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
 *
 * 장학은 제목 키워드보다 먼저 본다 — 장학 게시판 글이거나 본문에 "장학"이 있으면 장학이다.
 * ("2026년 든든 학업지원금 공고문" 같은 장학 공지가 제목의 '공고' 때문에 취업으로 분류됐다.)
 * '공고'는 학교 공지 대부분에 붙는 말이라 취업 키워드에서 뺐다(채용 공고는 '채용'으로 잡힌다).
 */
public final class NewsCategoryClassifier {

    private static final String DEFAULT_CATEGORY = "공지";
    private static final String SCHOLARSHIP = "장학";
    /** 이 게시판(BoardConfig.sourceId)에서 온 글은 제목과 상관없이 장학이다. */
    private static final String SCHOLARSHIP_BOARD = "장학";
    private static final Pattern SCHOLARSHIP_IN_CONTENT = Pattern.compile("장학");

    private static final List<Map.Entry<String, Pattern>> RULES = List.of(
            Map.entry("장학", Pattern.compile("장학|등록금|학자금")),
            Map.entry("취업", Pattern.compile("취업|채용|인턴|기업|박람회|연구원|모집")),
            Map.entry("수강", Pattern.compile("수강|성적|졸업|학점|교과|전공|시험|수업|계절학기|등록|휴학|복학")),
            Map.entry("행사", Pattern.compile("축제|행사|전시|공연|대회|특강|세미나|워크숍|해커톤|공모전")),
            Map.entry("시설", Pattern.compile("시설|공사|정전|단수|점검|보수|주차장|엘리베이터|소방|안전진단")),
            Map.entry("상담", Pattern.compile("상담|심리|건강|보건"))
    );

    private NewsCategoryClassifier() {
    }

    /**
     * 게시판·제목·본문으로 카테고리를 정한다.
     * 장학 게시판이거나 본문에 "장학"이 있으면 장학, 아니면 제목 키워드 규칙을 따른다.
     */
    public static String classify(String title, String content, String sourceId) {
        if (SCHOLARSHIP_BOARD.equals(sourceId)) return SCHOLARSHIP;
        String byTitle = classify(title);
        if (SCHOLARSHIP.equals(byTitle)) return SCHOLARSHIP;
        if (content != null && SCHOLARSHIP_IN_CONTENT.matcher(content).find()) return SCHOLARSHIP;
        return byTitle;
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
