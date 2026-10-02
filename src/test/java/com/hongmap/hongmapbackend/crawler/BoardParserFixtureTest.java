package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
import com.hongmap.hongmapbackend.crawler.parser.HongikBoardParser;
import com.hongmap.hongmapbackend.crawler.parser.ImwebBoardParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 새로 추가한 게시판의 실제 목록 HTML(2026.10.02 저장, src/test/resources/crawler)로 파서를 확인한다. 네트워크는 쓰지 않는다.
 */
class BoardParserFixtureTest {

    private final HongikBoardParser hongik = new HongikBoardParser();
    private final ImwebBoardParser imweb = new ImwebBoardParser();

    @Test
    void 디자인엔지니어링전공_목록() throws IOException {
        String listUrl = "https://smpd.hongik.ac.kr/smpd/0401.do";
        List<ArticleSummary> items = hongik.parseList(load("hongik-smpd-0401.html", listUrl), listUrl, "공지사항");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).articleNo()).isEqualTo("157208");
        assertThat(items.get(0).title()).isEqualTo("대학원 학과 장학금 관련 안내");
        assertThat(items.get(0).date()).isEqualTo("2026.09.17");
        assertThat(items.get(0).link()).startsWith("https://smpd.hongik.ac.kr/smpd/0401.do?mode=view&articleNo=157208");
    }

    @Test
    void 자율전공_목록() throws IOException {
        String listUrl = "https://fm.hongik.ac.kr/fm/0401.do";
        List<ArticleSummary> items = hongik.parseList(load("hongik-fm-0401.html", listUrl), listUrl, "공지사항");

        assertThat(items).hasSize(8);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.articleNo()).isNotBlank();
            assertThat(item.date()).matches("\\d{4}\\.\\d{2}\\.\\d{2}");
            assertThat(item.link()).startsWith("https://fm.hongik.ac.kr/fm/0401.do?mode=view&articleNo=");
        });
        assertThat(items.get(0).title()).isEqualTo("서울캠퍼스 자율전공 2026학년도 2학기 희망전공 신청 안내");
    }

    /** 조소과·기초과학과는 URL·표 summary·마크업이 다른 학과와 같고, 게시판에 글이 없을 뿐이다("등록된 글이 없습니다"). */
    @Test
    void 조소과와_기초과학과는_게시판이_비어_있어_0건이다() throws IOException {
        String scu = "https://scu.hongik.ac.kr/scu/0401.do";
        Document scuDoc = load("hongik-scu-0401-empty.html", scu);
        assertThat(scuDoc.selectFirst("table[summary=\"공지사항\"] td.b-no-post")).isNotNull();
        assertThat(hongik.parseList(scuDoc, scu, "공지사항")).isEmpty();

        String science = "https://science.hongik.ac.kr/science/0401.do";
        Document scienceDoc = load("hongik-science-0401-empty.html", science);
        assertThat(scienceDoc.selectFirst("table[summary=\"학과공지사항\"] td.b-no-post")).isNotNull();
        assertThat(hongik.parseList(scienceDoc, science, "학과공지사항")).isEmpty();
    }

    @Test
    void 바이오헬스융합학부_Imweb_목록() throws IOException {
        String listUrl = "https://biohealth.hongik.ac.kr/22";
        List<ArticleSummary> items = imweb.parseList(load("imweb-biohealth-22.html", listUrl), listUrl, null);

        assertThat(items).hasSize(10);
        ArticleSummary first = items.get(0);
        assertThat(first.articleNo()).isEqualTo("174569154");
        assertThat(first.title()).isEqualTo("[바이오헬스 혁신융합대학] 2026학년도 2학기 다전공 신청 안내");
        assertThat(first.date()).isEqualTo("2026.09.21");
        assertThat(first.link()).startsWith("https://biohealth.hongik.ac.kr/22/?").contains("bmode=view&idx=174569154");
    }

    private Document load(String name, String baseUri) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/crawler/" + name)) {
            assertThat(in).as(name).isNotNull();
            return Jsoup.parse(in, StandardCharsets.UTF_8.name(), baseUri);
        }
    }
}
