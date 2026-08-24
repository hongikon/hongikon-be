package com.hongmap.hongmapbackend.crawler.parser;

import com.hongmap.hongmapbackend.crawler.config.ParserType;
import org.jsoup.nodes.Document;

import java.util.List;

/**
 * 게시판 하나(CMS 종류 하나)를 크롤링하는 데 필요한 파싱 로직의 인터페이스.
 * hongmap(프론트) scripts/crawler/parsers.mjs의 {@code { buildListUrl, parseList, parseView }} 세 함수 묶음과 대응된다.
 *
 * 사이트별 실제 셀렉터 구현은 각 구현체에 있다 — HongikBoardParser가 기본(.do 게시판, 대부분의 게시판이 이 CMS)이고,
 * ArchBoardParser/ImwebBoardParser는 인터페이스만 채워져 있고 실제 파싱은 TODO 상태다.
 */
public interface BoardParser {

    ParserType type();

    /** 목록 URL에 페이지네이션 파라미터를 붙인다. */
    String buildListUrl(String listUrl, int page, int pageSize);

    /** 목록 HTML → 게시글 요약 목록(최신순). */
    List<ArticleSummary> parseList(Document document, String listUrl, String tableSummary);

    /** 상세 HTML → 본문/작성자/첨부파일. {@link #supportsDetail()}이 false면 호출되지 않는다. */
    ArticleDetail parseView(Document document, String pageUrl);

    /** 상세 페이지를 지원하지 않는 CMS(예: Imweb)는 false로 오버라이드한다. */
    default boolean supportsDetail() {
        return true;
    }
}
