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
 * 도시공학과 등 Imweb으로 만든 사이트 파서.
 *
 * 마크업 구조(2026.08 직접 접속 확인, urban.hongik.ac.kr/114 기준):
 * <pre>
 *   목록: 행마다 반복되는 &lt;ul class="li_body ..."&gt; 블록(공통 부모로 묶여있지 않다).
 *     &lt;a class="list_text_title" href="/114/?...&amp;bmode=view&amp;idx=173186865&amp;t=board"&gt;&lt;span&gt;제목&lt;/span&gt;&lt;/a&gt;
 *     &lt;li class="name"&gt;운영자&lt;/li&gt;
 *     &lt;li class="time" title="2026-08-18 15:13"&gt;2026-08-18&lt;/li&gt;  ← 표시 텍스트가 "N일전" 같은 상대 시간일 수 있어 title 속성만 신뢰한다.
 *     &lt;li class="read"&gt;&lt;span&gt;조회수&lt;/span&gt;83&lt;/li&gt;
 *
 *   상세: 목록과 페이지 크기가 다를 뿐(약 300KB, 대부분 인라인 테마 CSS) 파싱 경계 자체는 뚜렷하다.
 *     &lt;h1 class="view_tit"&gt;&lt;span class="sticker notice text-brand"&gt;공지&lt;/span&gt; 제목&lt;/h1&gt;
 *     &lt;div class="author"&gt;&lt;div class="write"&gt;운영자&lt;/div&gt;&lt;div class="date"&gt;2026-08-18&lt;/div&gt;&lt;div class="hit-count"&gt;조회수 83&lt;/div&gt;&lt;/div&gt;
 *     &lt;div class="board_txt_area fr-view"&gt; 본문(이미지 포함) &lt;/div&gt;
 *     &lt;div class="file_area"&gt;&lt;ul&gt;&lt;li&gt;&lt;a href="/post_file_download.cm?..."&gt;&lt;p class="tit"&gt;파일명&lt;/p&gt;&lt;/a&gt;&lt;/li&gt;&lt;/ul&gt;&lt;/div&gt;
 * </pre>
 * page 쿼리 파라미터만으로 페이지네이션이 동작함을 직접 확인했다(별도 페이지 크기 파라미터는 없다).
 */
@Component
public class ImwebBoardParser implements BoardParser {

    private static final Pattern IDX_PATTERN = Pattern.compile("[?&]idx=(\\d+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern VIEW_COUNT_PATTERN = Pattern.compile("조회수\\s*([\\d,]+)");

    @Override
    public ParserType type() {
        return ParserType.IMWEB;
    }

    @Override
    public String buildListUrl(String listUrl, int page, int pageSize) {
        return UriComponentsBuilder.fromUriString(listUrl)
                .queryParam("page", page + 1)
                .build()
                .toUriString();
    }

    @Override
    public List<ArticleSummary> parseList(Document document, String listUrl, String tableSummary) {
        Elements rows = document.select("ul.li_body");

        List<ArticleSummary> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Element row : rows) {
            ArticleSummary item = parseRow(row);
            if (item == null || !seen.add(item.articleNo())) continue;
            items.add(item);
        }
        return items;
    }

    private ArticleSummary parseRow(Element row) {
        Element link = row.selectFirst("a.list_text_title");
        if (link == null) return null;

        Matcher idxMatcher = IDX_PATTERN.matcher(link.attr("href"));
        if (!idxMatcher.find()) return null;

        Element titleSpan = link.selectFirst("span");
        String title = titleSpan != null ? titleSpan.text().trim() : link.text().trim();
        if (title.isEmpty()) return null;

        Element timeEl = row.selectFirst("li.time");
        String date = timeEl != null ? extractDate(timeEl.attr("title")) : "";

        return new ArticleSummary(
                idxMatcher.group(1),
                title,
                "",
                date,
                extractHitCount(row.selectFirst("li.read")),
                false,
                link.absUrl("href")
        );
    }

    @Override
    public ArticleDetail parseView(Document document, String pageUrl) {
        Element titleEl = document.selectFirst("h1.view_tit");
        String title = "";
        if (titleEl != null) {
            Element clone = titleEl.clone();
            clone.select("span.sticker").remove();
            title = clone.text().trim();
        }

        Element authorBox = document.selectFirst("div.author");
        String writer = textOrEmpty(authorBox != null ? authorBox.selectFirst("div.write") : null);
        String date = extractDate(textOrEmpty(authorBox != null ? authorBox.selectFirst("div.date") : null));
        Integer views = extractHitCount(authorBox != null ? authorBox.selectFirst("div.hit-count") : null);

        Element contentBox = document.selectFirst("div.board_txt_area.fr-view");
        String content = contentBox != null ? contentBox.text() : "";

        List<String> images = contentBox == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(contentBox.select("img[src]").eachAttr("abs:src")));

        return new ArticleDetail(title, writer, date, views, content, images, parseAttachments(document));
    }

    private List<Attachment> parseAttachments(Document document) {
        Element fileArea = document.selectFirst("div.file_area");
        if (fileArea == null) return List.of();

        List<Attachment> attachments = new ArrayList<>();
        for (Element a : fileArea.select("li a[href]")) {
            Element nameEl = a.selectFirst("p.tit");
            String name = nameEl != null ? nameEl.text().trim() : a.text().trim();
            if (name.isEmpty()) continue;
            attachments.add(new Attachment(name, a.absUrl("href")));
        }
        return attachments;
    }

    private String extractDate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        Matcher matcher = DATE_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) : "";
    }

    private Integer extractHitCount(Element element) {
        if (element == null) return null;

        Matcher matcher = VIEW_COUNT_PATTERN.matcher(element.text());
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
