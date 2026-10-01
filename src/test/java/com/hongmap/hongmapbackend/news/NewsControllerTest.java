package com.hongmap.hongmapbackend.news;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /news 페이지네이션·필터. H2 인메모리 DB(application-test.properties).
 * 다른 테스트가 저장한 소식과 섞이지 않게 테스트마다 고유한 category·sourceId로 25건을 넣고 그 값으로 필터링한다.
 *
 * 데이터(i = 0..24): 제목 "소식 i", i % 5 == 3 이면 제목 끝에 " 수강신청"(3, 8, 13, 18, 23).
 * sourceId는 i % 3 기준 A(0,3,…,24 — 9건) / B(8건) / C(8건).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NewsControllerTest {

    private static final int TOTAL = 25;

    @Autowired MockMvc mockMvc;
    @Autowired NewsRepository newsRepository;

    String category;
    String sourceA;
    String sourceB;
    String sourceC;

    @BeforeEach
    void setUp() {
        category = "t-" + UUID.randomUUID().toString().substring(0, 8);
        sourceA = "학사-" + category;
        sourceB = "장학-" + category;
        sourceC = "기타-" + category;
        String[] sources = {sourceA, sourceB, sourceC};
        LocalDateTime base = LocalDateTime.of(2026, 9, 1, 0, 0);
        for (int i = 0; i < TOTAL; i++) {
            newsRepository.save(News.builder()
                    .title("소식 " + i + (i % 5 == 3 ? " 수강신청" : ""))
                    .category(category)
                    .sourceId(sources[i % 3])
                    .sourceUrl("https://example.com/" + category + "/" + i)
                    // 5건씩 같은 작성일 — 같은 날짜가 많은 실제 데이터처럼 페이지 경계에서 순서가 흔들리지 않는지 본다.
                    .publishedAt(base.plusDays(i / 5))
                    .build());
        }
    }

    @Test
    void 첫_페이지는_size만큼_최신순으로_주고_전체_건수와_다음_페이지_여부를_알려준다() throws Exception {
        mockMvc.perform(get("/news").param("category", category).param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.content[0].title").value("소식 24"))
                .andExpect(jsonPath("$.content[9].title").value("소식 15"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(TOTAL))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void 마지막_페이지는_남은_건수만_주고_다음_페이지가_없다() throws Exception {
        mockMvc.perform(get("/news").param("category", category).param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].title").value("소식 4"))
                .andExpect(jsonPath("$.content[4].title").value("소식 0"))
                .andExpect(jsonPath("$.totalElements").value(TOTAL))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void size를_안_주면_20이고_50을_넘기면_50으로_맞춘다() throws Exception {
        mockMvc.perform(get("/news").param("category", category))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/news").param("category", category).param("size", "1000"))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.content", hasSize(TOTAL)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void sourceId를_여러_개_주면_그중_하나에_해당하는_글을_모두_준다() throws Exception {
        mockMvc.perform(get("/news").param("sourceId", sourceA).param("sourceId", sourceB).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(17))
                .andExpect(jsonPath("$.content", hasSize(17)))
                .andExpect(jsonPath("$.content[0].sourceId").value(sourceA));

        mockMvc.perform(get("/news").param("sourceId", sourceC))
                .andExpect(jsonPath("$.totalElements").value(8));
    }

    @Test
    void keyword는_제목_부분_일치로_찾고_다른_필터와_AND로_조합된다() throws Exception {
        mockMvc.perform(get("/news").param("category", category).param("keyword", "수강"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].title").value("소식 23 수강신청"));

        // 수강신청(3, 8, 13, 18, 23) ∩ sourceA(i % 3 == 0) = 3, 18
        mockMvc.perform(get("/news").param("keyword", "수강신청").param("sourceId", sourceA))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("소식 18 수강신청"))
                .andExpect(jsonPath("$.content[1].title").value("소식 3 수강신청"));
    }

    @Test
    void keyword의_퍼센트와_밑줄은_와일드카드가_아니라_글자_그대로_찾는다() throws Exception {
        mockMvc.perform(get("/news").param("category", category).param("keyword", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/news").param("category", category).param("keyword", "_"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
