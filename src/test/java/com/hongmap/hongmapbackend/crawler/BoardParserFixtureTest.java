package com.hongmap.hongmapbackend.crawler;

import com.hongmap.hongmapbackend.crawler.parser.ArticleDetail;
import com.hongmap.hongmapbackend.crawler.parser.ArticleSummary;
import com.hongmap.hongmapbackend.crawler.parser.Attachment;
import com.hongmap.hongmapbackend.crawler.parser.HongikBoardParser;
import com.hongmap.hongmapbackend.crawler.parser.ImwebBoardParser;
import com.hongmap.hongmapbackend.crawler.parser.OiaBoardParser;
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
    private final OiaBoardParser oia = new OiaBoardParser();

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

    // ---- 국제협력본부(inno.hongik.ac.kr/oia, 2026.10.12 저장) ----

    @Test
    void 국제교류실_1페이지는_상단_고정_5개와_일반_글_5개다() throws IOException {
        String listUrl = "https://inno.hongik.ac.kr/oia/board/1";
        List<ArticleSummary> items = oia.parseList(load("oia-board-1.html", listUrl), listUrl, null);

        assertThat(items).hasSize(10);
        assertThat(items.subList(0, 5)).allMatch(ArticleSummary::pinned);
        assertThat(items.subList(5, 10)).noneMatch(ArticleSummary::pinned);

        ArticleSummary pinned = items.get(0);
        assertThat(pinned.articleNo()).isEqualTo("527");
        assertThat(pinned.title()).isEqualTo("(★중요★) [건축도시대학 건축학부] 교류학생 대체학점 관련 안내사항");
        assertThat(pinned.date()).isEqualTo("2025.11.26");
        assertThat(pinned.views()).isEqualTo(1370);
        assertThat(pinned.link()).isEqualTo("https://inno.hongik.ac.kr/oia/boardview/1/527");

        // 제목 뒤 "새로운 글" 아이콘 이미지는 제목에 섞이지 않는다(title 속성 사용).
        ArticleSummary latest = items.get(5);
        assertThat(latest.articleNo()).isEqualTo("618");
        assertThat(latest.title()).isEqualTo("[한미교육위원단] 풀브라이트 미국유학 및 연구 장학금 설명회 안내(11.6)");
        assertThat(latest.date()).isEqualTo("2026.10.12");
        assertThat(latest.views()).isEqualTo(20);
    }

    @Test
    void 국제교류실_2페이지부터는_고정_없이_일반_글_10개다() throws IOException {
        String listUrl = "https://inno.hongik.ac.kr/oia/board/1";
        List<ArticleSummary> items = oia.parseList(load("oia-board-1-p2.html", listUrl + "/2"), listUrl, null);

        assertThat(items).hasSize(10).noneMatch(ArticleSummary::pinned);
        assertThat(items.get(0).articleNo()).isEqualTo("613");
        assertThat(items).allSatisfy(item -> {
            assertThat(item.date()).matches("\\d{4}\\.\\d{2}\\.\\d{2}");
            assertThat(item.link()).startsWith("https://inno.hongik.ac.kr/oia/boardview/1/");
        });
    }

    @Test
    void 글로벌역량_지원사업_목록() throws IOException {
        String listUrl = "https://inno.hongik.ac.kr/oia/board/13";
        List<ArticleSummary> items = oia.parseList(load("oia-board-13.html", listUrl), listUrl, null);

        assertThat(items).hasSize(10).noneMatch(ArticleSummary::pinned);
        assertThat(items.get(0).articleNo()).isEqualTo("78");
        assertThat(items.get(0).date()).isEqualTo("2024.09.13");
        assertThat(items.get(0).link()).isEqualTo("https://inno.hongik.ac.kr/oia/boardview/13/78");
    }

    // ---- 취업진로지원센터(inno.hongik.ac.kr/career, 2026.10.13 저장) — 국제협력본부와 같은 마크업 ----

    @Test
    void 취업진로지원센터_공지는_고정_글이_일반_목록에_또_나와도_한_번만_읽는다() throws IOException {
        String listUrl = "https://inno.hongik.ac.kr/career/board/1";
        List<ArticleSummary> items = oia.parseList(load("career-board-1.html", listUrl), listUrl, null);

        // 표는 10행(고정 5 + 일반 5)인데 일반 5행 중 899·898은 고정 글과 같은 글이라 8개가 남는다.
        assertThat(items).extracting(ArticleSummary::articleNo)
                .containsExactly("899", "898", "897", "890", "889", "902", "901", "900");
        assertThat(items.subList(0, 5)).allMatch(ArticleSummary::pinned);
        assertThat(items.subList(5, 8)).noneMatch(ArticleSummary::pinned);

        ArticleSummary latest = items.get(5);
        assertThat(latest.title()).isEqualTo("[대학혁신지원사업/취업진로지원센터] 2026학년도 NCS 집중교육(직업기초능력평가) 2기 참여자 모집");
        assertThat(latest.date()).isEqualTo("2026.10.12");
        assertThat(latest.link()).isEqualTo("https://inno.hongik.ac.kr/career/boardview/1/902");
    }

    @Test
    void 취업진로지원센터_채용정보_목록() throws IOException {
        String listUrl = "https://inno.hongik.ac.kr/career/board/2";
        List<ArticleSummary> items = oia.parseList(load("career-board-2.html", listUrl), listUrl, null);

        assertThat(items).hasSize(10).noneMatch(ArticleSummary::pinned);
        assertThat(items.get(1).articleNo()).isEqualTo("25443");
        assertThat(items.get(1).title()).isEqualTo("[일반채용][한진] 2027 신입사원 채용");
        assertThat(items.get(1).date()).isEqualTo("2026.10.13");
        assertThat(items.get(1).link()).isEqualTo("https://inno.hongik.ac.kr/career/boardview/2/25443");
        assertThat(oia.buildListUrl(listUrl, 1, 50)).isEqualTo(listUrl + "/2");
    }

    @Test
    void 국제협력본부_페이지는_경로로_넘긴다() {
        String listUrl = "https://inno.hongik.ac.kr/oia/board/1";
        assertThat(oia.buildListUrl(listUrl, 0, 50)).isEqualTo(listUrl);
        assertThat(oia.buildListUrl(listUrl, 1, 50)).isEqualTo(listUrl + "/2");
        assertThat(oia.buildListUrl(listUrl + "/", 2, 50)).isEqualTo(listUrl + "/3");
    }

    @Test
    void 국제교류실_상세는_아이콘_글자를_빼고_작성자_날짜_조회수를_읽는다() throws IOException {
        String pageUrl = "https://inno.hongik.ac.kr/oia/boardview/1/527";
        ArticleDetail detail = oia.parseView(load("oia-boardview-1-527.html", pageUrl), pageUrl);

        assertThat(detail.title()).isEqualTo("(★중요★) [건축도시대학 건축학부] 교류학생 대체학점 관련 안내사항");
        assertThat(detail.writer()).isEqualTo("조희경");
        assertThat(detail.date()).isEqualTo("2025.11.26");
        assertThat(detail.views()).isEqualTo(1370);
        // 첨부파일 목록(파일명)은 본문에 섞이지 않는다.
        assertThat(detail.content()).contains("대체학점으로 인정받고자 하는 경우").doesNotContain(".hwp");
        // 첨부는 ASP.NET postback이라 받을 주소가 없어 원문 페이지로 연결한다.
        assertThat(detail.attachments()).containsExactly(
                new Attachment("(양식)건축도시대학 교류학생 대체학점 사전승인 확인서.hwp", pageUrl));
    }

    private Document load(String name, String baseUri) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/crawler/" + name)) {
            assertThat(in).as(name).isNotNull();
            return Jsoup.parse(in, StandardCharsets.UTF_8.name(), baseUri);
        }
    }
}
