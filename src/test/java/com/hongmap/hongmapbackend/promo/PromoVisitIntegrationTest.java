package com.hongmap.hongmapbackend.promo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 홍보 링크: 로그인 없이 302 로 앱 받기 안내에 보내고, 사람 방문만 경로별로 센다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PromoVisitIntegrationTest {

    private static final String PHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) KAKAOTALK 10.0";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS promo_visits (source varchar(32) NOT NULL, "
                + "visit_date date NOT NULL, visits int NOT NULL DEFAULT 0, PRIMARY KEY (source, visit_date))");
        jdbcTemplate.update("DELETE FROM promo_visits");
    }

    private int clubVisits() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(visits), 0) FROM promo_visits WHERE source = 'club'", Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void 사람_방문은_세고_안내_페이지로_보낸다() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/promo/club").header("User-Agent", PHONE))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "https://hongikon.com/app"));
        }
        assertThat(clubVisits()).isEqualTo(2);
    }

    @Test
    void 미리보기_봇과_모르는_경로는_세지_않고_이동만() throws Exception {
        mockMvc.perform(get("/promo/club").header("User-Agent", "facebookexternalhit/1.1 kakaotalk-scrap/1.0"))
                .andExpect(status().isFound());
        mockMvc.perform(get("/promo/club")).andExpect(status().isFound()); // User-Agent 없음
        mockMvc.perform(get("/promo/nope").header("User-Agent", PHONE))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://hongikon.com/app"));
        assertThat(clubVisits()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM promo_visits", Integer.class)).isZero();
    }

    @Test
    void 테이블이_없어도_이동은_된다() throws Exception {
        jdbcTemplate.execute("DROP TABLE promo_visits");
        mockMvc.perform(get("/promo/club").header("User-Agent", PHONE))
                .andExpect(status().isFound());
    }

    private int visits(String source) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(visits), 0) FROM promo_visits WHERE source = ?", Integer.class, source);
        return n == null ? 0 : n;
    }

    @Test
    void 앱_받기_안내_페이지가_열리면_app_으로_세고_204() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/promo/app/seen").header("User-Agent", PHONE))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        assertThat(visits("app")).isEqualTo(3);
    }

    @Test
    void 페이지_방문은_봇을_세지_않고_app_외의_이름은_받지_않는다() throws Exception {
        mockMvc.perform(get("/promo/app/seen").header("User-Agent", "facebookexternalhit/1.1 kakaotalk-scrap/1.0"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/promo/club/seen").header("User-Agent", PHONE)).andExpect(status().isNoContent());
        assertThat(visits("app")).isZero();
        assertThat(visits("club")).isZero();
    }

    @Test
    void 홍보_링크로_app_을_불러도_페이지_방문과_겹쳐_세지_않는다() throws Exception {
        // /app/club → /promo/club(+1) → /app 으로 이동 → 페이지가 /promo/app/seen(+1). /promo/app 은 이동만 한다.
        mockMvc.perform(get("/promo/app").header("User-Agent", PHONE)).andExpect(status().isFound());
        assertThat(visits("app")).isZero();
    }

    @Test
    void 방문_수_조회는_관리자만() throws Exception {
        mockMvc.perform(get("/admin/promo-visits")).andExpect(status().isUnauthorized());
    }
}
