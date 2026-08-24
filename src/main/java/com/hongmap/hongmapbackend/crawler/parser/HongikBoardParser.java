package com.hongmap.hongmapbackend.crawler.parser;

import com.hongmap.hongmapbackend.crawler.config.ParserType;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 본부·학과 게시판이 쓰는 .do 게시판(기본 CMS) 파서. 가장 많은 게시판이 이 CMS를 쓴다.
 *
 * hongmap(프론트) scripts/crawler/parse-list.mjs, parse-view.mjs의 정규식 파싱을 Jsoup 셀렉터로 옮긴 것.
 * 마크업 구조(2026.08 확인, 프론트 코멘트 기준):
 * <pre>
 *   &lt;tr class="b-top-box "&gt;
 *     &lt;td class="b-num-box"&gt; 225 &lt;/td&gt;
 *     &lt;td class="b-td-left"&gt;
 *       &lt;a href="?mode=view&amp;articleNo=154811&amp;..."&gt;&lt;span class="b-title"&gt;제목&lt;/span&gt;&lt;/a&gt;
 *       &lt;div class="b-m-con"&gt;
 *         &lt;span class="hit"&gt;조회수 13&lt;/span&gt;
 *         &lt;span class="b-date"&gt;2026.08.04&lt;/span&gt;
 *         &lt;span class="b-file"&gt;첨부파일&lt;/span&gt;
 *       &lt;/div&gt;
 *     &lt;/td&gt;
 *   &lt;/tr&gt;
 *
 *   &lt;li class="b-writer-date-box"&gt;&lt;span&gt;컴퓨터공학과&lt;/span&gt;&lt;span&gt;2026.08.04&lt;/span&gt;&lt;/li&gt;
 *   &lt;li class="b-hit-box"&gt;&lt;span&gt;조회수 13&lt;/span&gt;&lt;/li&gt;
 *   &lt;div class="m-file-box pc-hide"&gt; ... &lt;a class="btn-file hwp" href="?mode=download&amp;..."&gt;파일명&lt;/a&gt;
 *   &lt;div class="b-content-box"&gt;&lt;div class="fr-view"&gt; 본문 &lt;/div&gt;&lt;/div&gt;
 * </pre>
 */
@Component
public class HongikBoardParser implements BoardParser {

    private static final Pattern ARTICLE_NO = Pattern.compile("articleNo=(\\d+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})");
    private static final Pattern DATE_ONLY = Pattern.compile("^\\d{4}\\.\\d{2}\\.\\d{2}$");
    private static final Pattern HIT_COUNT = Pattern.compile("조회수\\s*([\\d,]+)");
    private static final Pattern ATTACH_NO = Pattern.compile("attachNo=(\\d+)");

    @Override
    public ParserType type() {
        return ParserType.HONGIK;
    }

    @Override
    public String buildListUrl(String listUrl, int page, int pageSize) {
        return UriComponentsBuilder.fromUriString(listUrl)
                .queryParam("mode", "list")
                .queryParam("article.offset", page * pageSize)
                .queryParam("articleLimit", pageSize)
                .build()
                .toUriString();
    }

    @Override
    public List<ArticleSummary> parseList(Document document, String listUrl, String tableSummary) {
        Element scope = selectBoardTable(document, tableSummary);
        Elements rows = scope.select("tr");

        List<ArticleSummary> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Element row : rows) {
            ArticleSummary item = parseRow(row);
            if (item == null || !seen.add(item.articleNo())) continue;
            items.add(item);
        }
        return items;
    }

    /**
     * 게시판 표만 잘라낸다. 대학공지 페이지는 표 바깥에도 mode=view 링크가 있어서
     * 범위를 좁히지 않으면 관계없는 글이 섞여 들어온다.
     */
    private Element selectBoardTable(Document document, String tableSummary) {
        if (tableSummary == null || tableSummary.isBlank()) return document;

        Element table = document.selectFirst("table[summary=\"" + tableSummary + "\"]");
        return table != null ? table : document;
    }

    private ArticleSummary parseRow(Element row) {
        Element link = row.selectFirst("a[href*=\"mode=view\"]");
        if (link == null) return null;

        Matcher articleNoMatcher = ARTICLE_NO.matcher(link.attr("href"));
        if (!articleNoMatcher.find()) return null;

        Element titleSpan = row.selectFirst("span.b-title");
        String title = titleSpan != null ? titleSpan.text().trim() : "";
        if (title.isEmpty()) return null;

        return new ArticleSummary(
                articleNoMatcher.group(1),
                title,
                textOrEmpty(row.selectFirst("span.b-mini-cate")),
                extractDate(row),
                extractHitCount(row.selectFirst("span.hit")),
                row.selectFirst("span.b-file") != null,
                link.absUrl("href")
        );
    }

    @Override
    public ArticleDetail parseView(Document document, String pageUrl) {
        Element contentBox = document.selectFirst("div.b-content-box");
        String content = contentBox != null ? contentBox.text() : "";

        Element titleSpan = document.selectFirst("span.b-title");
        String title = titleSpan != null ? titleSpan.text().trim() : "";

        Element writerDateBox = document.selectFirst("li.b-writer-date-box");
        String writer = "";
        String date = "";
        if (writerDateBox != null) {
            for (Element span : writerDateBox.select("span")) {
                String text = span.text().trim();
                if (text.isEmpty()) continue;
                if (DATE_ONLY.matcher(text).matches()) {
                    date = text;
                } else if (writer.isEmpty()) {
                    writer = text;
                }
            }
        }

        Element hitBox = document.selectFirst("li.b-hit-box");
        Integer views = extractHitCount(hitBox);

        List<String> images = contentBox == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(contentBox.select("img[src]").eachAttr("abs:src")));

        return new ArticleDetail(title, writer, date, views, content, images, parseAttachments(document));
    }

    /**
     * 첨부파일 목록. 데스크톱/모바일 마크업이 같은 파일을 중복해서 담고 있으므로
     * 모바일 블록(m-file-box pc-hide)만 읽고 attachNo로 한 번 더 중복을 제거한다.
     */
    private List<Attachment> parseAttachments(Document document) {
        Element fileBox = document.selectFirst("div.m-file-box.pc-hide");
        if (fileBox == null) return List.of();

        Set<String> seenAttachNo = new HashSet<>();
        List<Attachment> attachments = new ArrayList<>();

        for (Element a : fileBox.select("a.btn-file")) {
            String href = a.attr("href");
            if (!href.contains("mode=download")) continue;

            Matcher attachNoMatcher = ATTACH_NO.matcher(href);
            String attachNo = attachNoMatcher.find() ? attachNoMatcher.group(1) : null;
            if (attachNo != null && !seenAttachNo.add(attachNo)) continue;

            String name = a.text().trim();
            if (name.isEmpty()) continue;

            attachments.add(new Attachment(name, a.absUrl("href")));
        }
        return attachments;
    }

    private String extractDate(Element row) {
        Element dateSpan = row.selectFirst("span.b-date");
        if (dateSpan != null) {
            Matcher exact = DATE_PATTERN.matcher(dateSpan.text());
            if (exact.find()) return exact.group();
        }

        Matcher fallback = DATE_PATTERN.matcher(row.text());
        return fallback.find() ? fallback.group() : "";
    }

    private Integer extractHitCount(Element element) {
        if (element == null) return null;

        Matcher matcher = HIT_COUNT.matcher(element.text());
        if (!matcher.find()) return null;

        try {
            return Integer.parseInt(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String textOrEmpty(Element element) {
        return element != null ? element.text().trim() : "";
    }
}
