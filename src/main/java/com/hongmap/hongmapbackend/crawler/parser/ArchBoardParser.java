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
 * 건축학부(arch.hongik.ac.kr) PHP CMS 파서. 본부·학과 게시판과 마크업이 아예 다르다.
 *
 * 마크업 구조(2026.08 직접 접속 확인):
 * <pre>
 *   목록 — notice.php(div.board_listS01)와 event.php(div.board_listS02)는 제목 위치가 다르다:
 *   &lt;div class="board_listS01"&gt;&lt;ul&gt;
 *     &lt;li&gt;
 *       &lt;a href="/kor/news/notice.php?m=v&amp;idx=u6XBcQ...&amp;pNo=1&amp;code=..."&gt;
 *         &lt;div class="info_bx"&gt;&lt;span&gt;No.444&lt;/span&gt;&lt;span&gt;2026.08.24&lt;/span&gt;&lt;/div&gt;
 *         &lt;div class="txt_bx"&gt;&lt;p&gt;제목&lt;/p&gt;&lt;/div&gt;  ← notice.php는 제목이 여기
 *       &lt;/a&gt;
 *     &lt;/li&gt;
 *   &lt;/ul&gt;&lt;/div&gt;
 *
 *   &lt;div class="board_listS02"&gt;&lt;ul&gt;
 *     &lt;li&gt;
 *       &lt;a href="/kor/news/event.php?m=v&amp;idx=xOX2Ot...&amp;pNo=1&amp;code=..."&gt;
 *         &lt;div class="img_bx"&gt;&lt;img src="..."&gt;&lt;/div&gt;
 *         &lt;div class="info_bx"&gt;&lt;strong&gt;제목&lt;/strong&gt;&lt;span&gt;작성일  2026.08.10&lt;/span&gt;&lt;/div&gt;  ← event.php는 제목이 여기(strong)
 *       &lt;/a&gt;
 *     &lt;/li&gt;
 *   &lt;/ul&gt;&lt;/div&gt;
 *
 *   상세(공지/행사 공통):
 *   &lt;div class="v_tit"&gt;
 *     &lt;strong&gt;제목&lt;/strong&gt;
 *     &lt;ul class="info2"&gt;&lt;li&gt;작성일  2026. 08. 24&lt;/li&gt;&lt;li&gt;조회수  7&lt;/li&gt;&lt;/ul&gt;
 *   &lt;/div&gt;
 *   &lt;div class="v_con"&gt; 본문(이미지 포함) &lt;/div&gt;
 *   &lt;div class="v_file"&gt;&lt;strong&gt;첨부파일&lt;/strong&gt;&lt;div&gt;&lt;a href="../../aseoul_bbs/down.php?...">파일명&lt;/a&gt;&lt;/div&gt;&lt;/div&gt;
 * </pre>
 * pNo만 붙여도 페이지네이션이 동작해 목록 CSRF 성격의 code 파라미터는 붙이지 않아도 된다(직접 확인).
 */
@Component
public class ArchBoardParser implements BoardParser {

    private static final Pattern IDX_PATTERN = Pattern.compile("[?&]idx=([^&]+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})\\.\\s*(\\d{2})\\.\\s*(\\d{2})");
    private static final Pattern VIEW_COUNT_PATTERN = Pattern.compile("조회수\\s*([\\d,]+)");

    @Override
    public ParserType type() {
        return ParserType.ARCH;
    }

    @Override
    public String buildListUrl(String listUrl, int page, int pageSize) {
        return UriComponentsBuilder.fromUriString(listUrl)
                .queryParam("pNo", page + 1)
                .build()
                .toUriString();
    }

    @Override
    public List<ArticleSummary> parseList(Document document, String listUrl, String tableSummary) {
        Elements rows = document.select("div.board_listS01 li, div.board_listS02 li");

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
        Element link = row.selectFirst("a[href*=\"m=v\"]");
        if (link == null) return null;

        Matcher idxMatcher = IDX_PATTERN.matcher(link.attr("href"));
        if (!idxMatcher.find()) return null;

        // notice.php(board_listS01)는 div.txt_bx p, event.php(board_listS02)는 div.info_bx strong에 제목이 있다.
        Element titleEl = row.selectFirst("div.txt_bx p, div.info_bx strong");
        String title = titleEl != null ? titleEl.text().trim() : "";
        if (title.isEmpty()) return null;

        return new ArticleSummary(
                idxMatcher.group(1),
                title,
                "",
                extractDate(row.selectFirst("div.info_bx")),
                null,
                false,
                link.absUrl("href")
        );
    }

    @Override
    public ArticleDetail parseView(Document document, String pageUrl) {
        Element titleEl = document.selectFirst("div.v_tit strong");
        String title = titleEl != null ? titleEl.text().trim() : "";

        String date = "";
        Integer views = null;
        Element infoList = document.selectFirst("div.v_tit ul.info2");
        if (infoList != null) {
            for (Element li : infoList.select("li")) {
                String text = li.text();
                Matcher dateMatcher = DATE_PATTERN.matcher(text);
                if (dateMatcher.find()) {
                    date = normalizeDate(dateMatcher);
                    continue;
                }
                Matcher viewMatcher = VIEW_COUNT_PATTERN.matcher(text);
                if (viewMatcher.find()) {
                    views = parseCount(viewMatcher.group(1));
                }
            }
        }

        Element contentBox = document.selectFirst("div.v_con");
        String content = contentBox != null ? contentBox.text() : "";

        List<String> images = contentBox == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(contentBox.select("img[src]").eachAttr("abs:src")));

        return new ArticleDetail(title, "", date, views, content, images, parseAttachments(document));
    }

    private List<Attachment> parseAttachments(Document document) {
        Element fileBox = document.selectFirst("div.v_file");
        if (fileBox == null) return List.of();

        List<Attachment> attachments = new ArrayList<>();
        for (Element a : fileBox.select("a[href]")) {
            String name = a.text().trim();
            if (name.isEmpty()) continue;
            attachments.add(new Attachment(name, a.absUrl("href")));
        }
        return attachments;
    }

    private String extractDate(Element infoBx) {
        if (infoBx == null) return "";
        Matcher matcher = DATE_PATTERN.matcher(infoBx.text());
        return matcher.find() ? normalizeDate(matcher) : "";
    }

    /** "2026. 08. 24" 같이 점 뒤에 공백이 섞여도 "yyyy.MM.dd"로 통일한다. */
    private String normalizeDate(Matcher matcher) {
        return matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3);
    }

    private Integer parseCount(String raw) {
        try {
            return Integer.parseInt(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
