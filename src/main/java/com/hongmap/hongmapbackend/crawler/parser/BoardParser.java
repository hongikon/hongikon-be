package com.hongmap.hongmapbackend.crawler.parser;

import com.hongmap.hongmapbackend.crawler.config.ParserType;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 게시판 하나(CMS 종류 하나)를 크롤링하는 데 필요한 파싱 로직의 인터페이스.
 * hongmap(프론트) scripts/crawler/parsers.mjs의 {@code { buildListUrl, parseList, parseView }} 세 함수 묶음과 대응된다.
 *
 * 사이트별 실제 셀렉터 구현은 각 구현체에 있다 — HongikBoardParser가 기본(.do 게시판, 대부분의 게시판이 이 CMS)이고,
 * ArchBoardParser(arch.hongik.ac.kr PHP CMS)/ImwebBoardParser(Imweb)도 실제 마크업을 확인해 구현을 채워뒀다.
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

    /**
     * 같은 글의 상세 링크가 요청할 때마다 같은 값인지. 크롤러는 기본적으로 링크(news.source_url)로 중복을 판단하는데,
     * 링크가 매번 바뀌는 CMS(예: 건축학부)는 false로 오버라이드해 게시판 출처+제목+작성일로 판단하게 한다.
     */
    default boolean hasStableArticleUrl() {
        return true;
    }

    /**
     * 본문 영역의 img src를 절대 URL로 모은다(순서 유지, 중복 제거).
     * data: URI(본문에 base64로 직접 박힌 이미지)는 한 장에 수십만 자가 되어 images 컬럼(TEXT)을 넘기므로 제외한다.
     */
    static List<String> extractImageUrls(Element contentBox) {
        if (contentBox == null) return List.of();
        return contentBox.select("img[src]").eachAttr("abs:src").stream()
                .filter(src -> !src.regionMatches(true, 0, "data:", 0, 5))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
