package com.hongmap.hongmapbackend.notice;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 앱 공지사항: GET /notices(게시 시각이 지난 것만·팝업 기간)와 /admin/notices(CRUD·권한·검증).
 * 다른 테스트와 H2 를 같이 쓰므로 이 테스트의 제목 접두어로 거른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppNoticeIntegrationTest {

    /** 기준 시각(UTC, 지금). 시계를 바꿔 끼우면 테스트 컨텍스트가 하나 더 생겨 메모리가 모자라서 지금 기준으로 쓴다. */
    LocalDateTime now;

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired AppNoticeRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JsonMapper jsonMapper;

    User admin;
    User normal;
    String prefix;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        admin = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("관리자").build());
        normal = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        prefix = "테스트공지-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM app_notices WHERE title LIKE '테스트공지-%'");
    }

    @Test
    void 앱은_게시_시각이_지난_공지만_최근_순으로_받고_팝업은_기간_안에만_붙는다() throws Exception {
        AppNotice old = save("지난 공지", now.minusDays(3), null, null);
        AppNotice popupOn = save("팝업 중", now.minusHours(1), now.plusDays(2), "요약");
        AppNotice popupOver = save("팝업 끝남", now.minusDays(2), now.minusDays(1), "끝난 요약");
        AppNotice exactlyNow = save("지금 게시", now, null, null);
        AppNotice scheduled = save("예약", now.plusMinutes(10), now.plusDays(1), "아직");

        List<JsonNode> mine = mine(get("/notices"));
        assertThat(mine.stream().map(n -> n.get("id").asLong()).toList())
                .containsExactly(exactlyNow.getId(), popupOn.getId(), popupOver.getId(), old.getId());

        JsonNode on = mine.get(1);
        assertThat(on.get("title").asString()).isEqualTo(prefix + "팝업 중");
        assertThat(on.get("body").asString()).isEqualTo("본문");
        assertThat(on.get("publishedAt").asString()).startsWith(now.minusHours(1).toString().substring(0, 16));
        assertThat(on.get("popup").get("text").asString()).isEqualTo("요약");
        assertThat(on.get("popup").get("until").asString()).startsWith(now.plusDays(2).toString().substring(0, 16));
        assertThat(mine.get(2).has("popup")).isFalse();
        assertThat(mine.get(0).has("popup")).isFalse();

        // 예약 시각이 되면 보인다(시각을 지난 것으로 바꿔 확인)
        jdbcTemplate.update("UPDATE app_notices SET publish_at = ? WHERE id = ?", now.minusSeconds(1), scheduled.getId());
        List<JsonNode> later = mine(get("/notices"));
        assertThat(later).hasSize(5);
        assertThat(later.get(1).get("id").asLong()).isEqualTo(scheduled.getId());  // 지금 게시(now) 다음
        assertThat(later.get(1).get("popup").get("text").asString()).isEqualTo("아직");
    }

    @Test
    void 공지_목록은_게스트도_본다() throws Exception {
        mockMvc.perform(get("/notices")).andExpect(status().isOk()).andExpect(jsonPath("$.notices").isArray());
    }

    @Test
    void 관리자는_예약_공지를_쓰고_고치고_지운다() throws Exception {
        String body = """
                {"title":"%s예약 공지","body":"  내일 아침부터 보여요  ","publishAt":"%s",
                 "popupUntil":"%s","popupText":" 내일부터 새 기능 "}
                """.formatted(prefix, iso(now.plusDays(1)), iso(now.plusDays(3)));
        MvcResult created = mockMvc.perform(post("/admin/notices").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.publishAt").value(now.plusDays(1).toString()))
                .andExpect(jsonPath("$.body").value("내일 아침부터 보여요"))
                .andExpect(jsonPath("$.popupText").value("내일부터 새 기능"))
                .andReturn();
        long id = jsonMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();

        assertThat(mine(get("/notices"))).isEmpty();
        List<JsonNode> adminList = mine(get("/admin/notices").header("Authorization", bearer(admin)));
        assertThat(adminList).hasSize(1);
        assertThat(adminList.get(0).get("status").asString()).isEqualTo("SCHEDULED");

        // 바로 게시로 고치고 팝업을 끈다 — popupText 는 버린다
        mockMvc.perform(put("/admin/notices/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"%s바로 공지","body":"본문","publishAt":"%s","popupText":"남지 않음"}
                                """.formatted(prefix, iso(now.minusHours(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.popupText").doesNotExist());
        List<JsonNode> published = mine(get("/notices"));
        assertThat(published).hasSize(1);
        assertThat(published.get(0).get("title").asString()).isEqualTo(prefix + "바로 공지");
        assertThat(published.get(0).has("popup")).isFalse();

        mockMvc.perform(delete("/admin/notices/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        assertThat(mine(get("/notices"))).isEmpty();
        mockMvc.perform(delete("/admin/notices/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 일반_사용자와_게스트는_관리자_공지_API_를_못_쓴다() throws Exception {
        String body = """
                {"title":"%s막힘","body":"본문","publishAt":"2020-01-01T00:00:00Z"}
                """.formatted(prefix);
        mockMvc.perform(post("/admin/notices").header("Authorization", bearer(normal))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/notices").header("Authorization", bearer(normal))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/notices").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is4xxClientError());
        assertThat(repository.findAll().stream().filter(n -> n.getTitle().startsWith(prefix))).isEmpty();
    }

    @Test
    void 잘못된_공지는_400() throws Exception {
        bad("""
                {"title":" ","body":"본문","publishAt":"2020-01-01T00:00:00Z"}""");
        bad("""
                {"title":"%s제목","body":"본문"}""".formatted(prefix));
        bad("""
                {"title":"%s제목","body":"%s","publishAt":"2020-01-01T00:00:00Z"}""".formatted(prefix, "가".repeat(5001)));
        // 팝업 문구 없이 팝업
        bad("""
                {"title":"%s제목","body":"본문","publishAt":"2020-01-01T00:00:00Z","popupUntil":"2020-01-02T00:00:00Z"}""".formatted(prefix));
        // 팝업 종료가 게시보다 앞
        bad("""
                {"title":"%s제목","body":"본문","publishAt":"2020-01-01T00:00:00Z","popupUntil":"2020-01-01T00:00:00Z","popupText":"요약"}""".formatted(prefix));
        // 팝업 31일 넘음
        bad("""
                {"title":"%s제목","body":"본문","publishAt":"2020-01-01T00:00:00Z","popupUntil":"2020-02-01T00:00:01Z","popupText":"요약"}""".formatted(prefix));
        // 1년 넘는 예약
        bad("""
                {"title":"%s제목","body":"본문","publishAt":"%s"}""".formatted(prefix, iso(now.plusDays(367))));
        assertThat(repository.findAll().stream().filter(n -> n.getTitle().startsWith(prefix))).isEmpty();
    }

    private void bad(String body) throws Exception {
        mockMvc.perform(post("/admin/notices").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    private AppNotice save(String title, LocalDateTime publishAt, LocalDateTime popupUntil, String popupText) {
        return repository.save(AppNotice.builder().title(prefix + title).body("본문").publishAt(publishAt)
                .popupUntil(popupUntil).popupText(popupText).build());
    }

    /** 응답의 notices 중 이 테스트가 만든 것만. */
    private List<JsonNode> mine(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsByteArray());
        List<JsonNode> out = new ArrayList<>();
        body.get("notices").forEach(n -> {
            if (n.get("title").asString().startsWith(prefix)) out.add(n);
        });
        return out;
    }

    private static String iso(LocalDateTime utc) {
        return utc + "Z";
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }
}
