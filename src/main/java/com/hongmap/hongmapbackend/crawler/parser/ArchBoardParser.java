package com.hongmap.hongmapbackend.crawler.parser;

import com.hongmap.hongmapbackend.crawler.config.ParserType;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * 건축학부(arch.hongik.ac.kr) PHP CMS 파서. 본부·학과 게시판과 마크업이 아예 다르다.
 *
 * TODO: parseList/parseView 미구현. 페이지네이션 파라미터(pNo)만 먼저 맞춰뒀다.
 * 참고: hongmap(프론트) scripts/crawler/parse-list-arch.mjs
 *   - 목록: div.board_listS01(공지) 또는 div.board_listS02(행사) 안의 li, 링크는 m=v&idx=번호
 *   - 상세: div.v_tit 안 strong이 제목, div.v_con이 본문(경계는 div.paging 앞까지)
 */
@Component
public class ArchBoardParser implements BoardParser {

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
        throw new UnsupportedOperationException(
                "ArchBoardParser.parseList 미구현 — scripts/crawler/parse-list-arch.mjs 참고해 구현 필요");
    }

    @Override
    public ArticleDetail parseView(Document document, String pageUrl) {
        throw new UnsupportedOperationException(
                "ArchBoardParser.parseView 미구현 — scripts/crawler/parse-list-arch.mjs 참고해 구현 필요");
    }
}
