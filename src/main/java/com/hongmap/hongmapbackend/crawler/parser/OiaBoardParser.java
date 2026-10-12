package com.hongmap.hongmapbackend.crawler.parser;

import com.hongmap.hongmapbackend.crawler.config.ParserType;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 국제협력본부(inno.hongik.ac.kr/oia) 게시판 파서. ASP.NET으로 만든 사이트라 학교 .do 게시판과 마크업이 다르다.
 *
 * 마크업 구조(2026.10.12 직접 접속 확인, /oia/board/1 국제교류실 공지사항 기준):
 * <pre>
 *   목록: &lt;table id="tablelist"&gt;&lt;tbody&gt;&lt;tr&gt; 한 행이 글 하나. 페이지당 10행 고정(크기 파라미터 없음).
 *     &lt;td&gt;&lt;span class="badge badge-secondary"&gt;공지&lt;/span&gt;&lt;/td&gt;   ← 상단 고정 행. 일반 행은 순번 숫자.
 *     &lt;td class="text-left"&gt;&lt;a href="/oia/boardview/1/527" title="제목"&gt;제목&lt;a&gt;&lt;/td&gt;   ← 닫는 태그가 &lt;a&gt;로 잘못 찍혀 있어 title 속성을 쓴다.
 *     &lt;td&gt;작성자&lt;/td&gt;&lt;td&gt;2025.11.26&lt;/td&gt;&lt;td&gt;조회수&lt;/td&gt;
 *   상단 고정 행은 1페이지에만 나오고, 그만큼 일반 글 자리를 차지한다(1페이지 일반 글 5개, 2페이지부터 10개).
 *   페이지는 경로로 넘긴다: /oia/board/1, /oia/board/1/2, ... 범위를 넘으면 빈 표가 온다.
 *
 *   상세: /oia/boardview/1/527
 *     &lt;div class="board-view-title"&gt;&lt;h4&gt;&lt;a class="title"&gt;제목&lt;/a&gt;&lt;/h4&gt;
 *       &lt;p class="board-info"&gt;&lt;span&gt;&lt;i class="material-icons"&gt;create&lt;/i&gt; 작성자&lt;/span&gt;
 *         &lt;span&gt;&lt;i&gt;access_time&lt;/i&gt; 2025.11.26 14:05&lt;/span&gt;&lt;span&gt;&lt;i&gt;visibility&lt;/i&gt; 1370&lt;/span&gt;&lt;/p&gt;
 *     &lt;div class="board-view-files"&gt;&lt;ul&gt;&lt;li&gt;&lt;a href="javascript:__doPostBack(...)"&gt;파일명&lt;/a&gt;
 *     &lt;div class="board-view-cont"&gt; 본문 &lt;/div&gt;
 * </pre>
 * 첨부파일은 ASP.NET postback이라 직접 받을 수 있는 주소가 없다. 이름만 남기고 링크는 원문 페이지로 둔다.
 */
@Component
public class OiaBoardParser implements BoardParser {

    /** 사이트가 페이지당 행 수를 10으로 고정해 둔다. */
    static final int PAGE_SIZE = 10;

    private static final Pattern ARTICLE_NO_PATTERN = Pattern.compile("/boardview/\\d+/(\\d+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[\\d,]+$");

    @Override
    public ParserType type() {
        return ParserType.OIA;
    }

    @Override
    public String buildListUrl(String listUrl, int page, int pageSize) {
        if (page == 0) return listUrl;
        String base = listUrl.endsWith("/") ? listUrl.substring(0, listUrl.length() - 1) : listUrl;
        return base + "/" + (page + 1);
    }

    @Override
    public int fixedPageSize() {
        return PAGE_SIZE;
    }

    @Override
    public boolean detectsPinned() {
        return true;
    }

    @Override
    public List<ArticleSummary> parseList(Document document, String listUrl, String tableSummary) {
        List<ArticleSummary> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Element row : document.select("table#tablelist tbody tr")) {
            ArticleSummary item = parseRow(row);
            if (item == null || !seen.add(item.articleNo())) continue;
            items.add(item);
        }
        return items;
    }

    private ArticleSummary parseRow(Element row) {
        Element link = row.selectFirst("a[href*=/boardview/]");
        if (link == null) return null;

        Matcher articleNo = ARTICLE_NO_PATTERN.matcher(link.attr("href"));
        if (!articleNo.find()) return null;

        String title = link.attr("title").trim();
        if (title.isEmpty()) title = link.text().trim();
        if (title.isEmpty()) return null;

        String date = "";
        Integer views = null;
        for (Element cell : row.select("> td")) {
            String text = cell.text().trim();
            if (date.isEmpty() && DATE_PATTERN.matcher(text).matches()) {
                date = text;
            } else if (!date.isEmpty() && NUMBER_PATTERN.matcher(text).matches()) {
                // 조회수는 작성일 다음 칸이다. 첫 칸의 순번 숫자와 헷갈리지 않게 작성일 뒤에서만 읽는다.
                views = parseNumber(text);
            }
        }

        boolean pinned = row.selectFirst("> td:first-child span.badge") != null;

        return new ArticleSummary(articleNo.group(1), title, "", date, views, false, link.absUrl("href"), pinned);
    }

    @Override
    public ArticleDetail parseView(Document document, String pageUrl) {
        Element titleEl = document.selectFirst("div.board-view-title h4");
        String title = titleEl != null ? titleEl.text().trim() : "";

        String writer = "";
        String date = "";
        Integer views = null;
        Element info = document.selectFirst("div.board-view-title p.board-info");
        if (info != null) {
            for (Element span : info.select("> span")) {
                Element icon = span.selectFirst("i.material-icons");
                String kind = icon != null ? icon.text().trim() : "";
                // 아이콘이 글자(ligature)로 들어 있어 그대로 text()하면 "create 조희경"이 된다 — 아이콘을 빼고 읽는다.
                Element clone = span.clone();
                clone.select("i").remove();
                String value = clone.text().trim();
                switch (kind) {
                    case "create" -> writer = value;
                    case "access_time" -> date = extractDate(value);
                    case "visibility" -> views = parseNumber(value);
                    default -> {
                    }
                }
            }
        }

        Element contentBox = document.selectFirst("div.board-view-cont");
        String content = contentBox != null ? contentBox.text() : "";
        List<String> images = BoardParser.extractImageUrls(contentBox);

        return new ArticleDetail(title, writer, date, views, content, images, parseAttachments(document, pageUrl));
    }

    private List<Attachment> parseAttachments(Document document, String pageUrl) {
        List<Attachment> attachments = new ArrayList<>();
        for (Element a : document.select("div.board-view-files li a")) {
            String name = a.text().trim();
            if (!name.isEmpty()) attachments.add(new Attachment(name, pageUrl));
        }
        return attachments;
    }

    private String extractDate(String raw) {
        Matcher matcher = DATE_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) : "";
    }

    private Integer parseNumber(String raw) {
        try {
            return Integer.parseInt(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
