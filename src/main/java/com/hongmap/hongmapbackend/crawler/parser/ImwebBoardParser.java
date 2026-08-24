package com.hongmap.hongmapbackend.crawler.parser;

import com.hongmap.hongmapbackend.crawler.config.ParserType;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * 도시공학과 등 Imweb으로 만든 사이트 파서.
 *
 * TODO: parseList 미구현. 페이지네이션 파라미터(page)만 먼저 맞춰뒀다.
 * 참고: hongmap(프론트) scripts/crawler/parse-list-imweb.mjs
 *   - 목록: ul.li_body 블록 반복, 링크는 a.list_text_title의 href(쿼리 idx=번호),
 *     날짜는 li.time의 title 속성(YYYY-MM-DD)에 있다(목록에 보이는 텍스트는 "N일전" 같은 상대 시간이라 못 쓴다).
 *
 * 상세는 프론트에서도 지원하지 않는다(본문이 340KB를 넘고 경계가 뚜렷하지 않아 깨지기 쉽다는 코멘트가 있다) —
 * supportsDetail()도 false로 유지한다.
 */
@Component
public class ImwebBoardParser implements BoardParser {

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
        throw new UnsupportedOperationException(
                "ImwebBoardParser.parseList 미구현 — scripts/crawler/parse-list-imweb.mjs 참고해 구현 필요");
    }

    @Override
    public ArticleDetail parseView(Document document, String pageUrl) {
        throw new UnsupportedOperationException("Imweb 게시판은 상세를 지원하지 않는다");
    }

    @Override
    public boolean supportsDetail() {
        return false;
    }
}
