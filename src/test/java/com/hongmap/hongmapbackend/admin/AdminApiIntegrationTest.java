package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 API 권한(401/403/200)과 제보 승인·문의 흐름. H2 인메모리 DB(application-test.properties).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    User admin;
    User normal;
    Building building;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("관리자").build());
        normal = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        building = buildingRepository.save(Building.builder()
                .name("테스트관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private Report pendingReport() {
        LocalDateTime now = LocalDateTime.now();
        return reportRepository.save(Report.builder()
                .user(normal).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .build());
    }

    @Test
    void 관리자_API는_비로그인_401_일반사용자_403_관리자_200() throws Exception {
        mockMvc.perform(get("/admin/overview")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/overview").header("Authorization", bearer(normal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/overview").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.pending").isNumber())
                .andExpect(jsonPath("$.crawler.running").value(false));
    }

    @Test
    void 대시보드_노출중은_지금_지도에_보이는_승인_제보만_센다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        // 이 클래스는 테스트마다 DB 를 비우지 않아 다른 테스트의 제보가 남는다 — 전후 차이로 본다.
        long before = reportRepository.countLive(ReportStatus.ACTIVE, now);
        Report live = pendingReport();
        live.moderate(ReportStatus.ACTIVE, null, now);
        reportRepository.save(live);
        // 승인했지만 끝나는 시각이 지난 제보 — 지도 목록에서 빠지므로 '노출 중'에 넣지 않는다.
        Report ended = reportRepository.save(Report.builder()
                .user(normal).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.EVENT).title("끝난 행사")
                .startsAt(now.minusHours(5)).endsAt(now.minusHours(1))
                .build());
        ended.moderate(ReportStatus.ACTIVE, null, now.minusHours(4));
        reportRepository.save(ended);
        // 승인했지만 아직 시작 전인 예정 제보 — 시작 시각 전에는 지도에 없으므로 '노출 중'에 넣지 않는다.
        Report upcoming = reportRepository.save(Report.builder()
                .user(normal).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.EVENT).title("내일 행사")
                .startsAt(now.plusHours(20)).endsAt(now.plusHours(23))
                .build());
        upcoming.moderate(ReportStatus.ACTIVE, null, now);
        reportRepository.save(upcoming);

        mockMvc.perform(get("/admin/overview").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.active").value((int) before + 1));

        // 이 클래스는 DB 를 비우지 않는다 — 진행 중 제보가 남으면 "지도 목록이 비어 있다"고 보는 다른 테스트가 깨진다.
        reportRepository.deleteAll(List.of(live, ended, upcoming));
    }

    @Test
    void 제보_검토_목록을_등록일_한국날짜로_거른다() throws Exception {
        Report early = pendingReport();
        Report boundary = pendingReport();
        Report late = pendingReport();
        // DB 시각은 UTC. 한국 10/3 01:00 = UTC 10/2 16:00 — 한국 날짜로는 10/3 이라 10/3 조회에 들어가야 한다.
        jdbcTemplate.update("UPDATE reports SET created_at = ? WHERE id = ?", LocalDateTime.of(2026, 10, 2, 14, 0), early.getId());
        jdbcTemplate.update("UPDATE reports SET created_at = ? WHERE id = ?", LocalDateTime.of(2026, 10, 2, 16, 0), boundary.getId());
        jdbcTemplate.update("UPDATE reports SET created_at = ? WHERE id = ?", LocalDateTime.of(2026, 10, 4, 1, 0), late.getId());

        mockMvc.perform(get("/admin/reports").param("status", "ALL").param("from", "2026-10-03").param("to", "2026-10-03")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value(1))
                .andExpect(jsonPath("$.reports[0].id").value(boundary.getId()));

        mockMvc.perform(get("/admin/reports").param("status", "ALL").param("from", "2026-10-02").param("to", "2026-10-04")
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.reports[?(@.id == %d)]", early.getId()).exists())
                .andExpect(jsonPath("$.reports[?(@.id == %d)]", boundary.getId()).exists())
                .andExpect(jsonPath("$.reports[?(@.id == %d)]", late.getId()).exists());

        mockMvc.perform(get("/admin/reports").param("from", "2026-10-05").param("to", "2026-10-01")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 크롤링_수동실행과_백필은_일반사용자에게_막힌다() throws Exception {
        mockMvc.perform(post("/crawler/trigger").header("Authorization", bearer(normal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/news/backfill-location").header("Authorization", bearer(normal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 승인하면_지도_제보목록에_노출된다() throws Exception {
        Report report = pendingReport();

        mockMvc.perform(get("/reports")).andExpect(jsonPath("$.reports.length()").value(0));
        mockMvc.perform(get("/admin/reports").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.reports[0].id").value(report.getId()))
                .andExpect(jsonPath("$.reports[0].buildingName").value(building.getName()))
                .andExpect(jsonPath("$.reports[0].authorDisplayName").value("학*"))
                .andExpect(jsonPath("$.reports[0].authorNickname").value("학*"))
                .andExpect(jsonPath("$.reports[0].authorMemberCode").value(normal.getMemberCode()))
                .andExpect(jsonPath("$.reports[0].flagCount").value(0));

        mockMvc.perform(patch("/admin/reports/" + report.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

        mockMvc.perform(get("/reports")).andExpect(jsonPath("$.reports[0].id").value(report.getId()));
    }

    @Test
    void 반려는_사유가_필요하고_PENDING으로는_되돌릴_수_없다() throws Exception {
        Report report = pendingReport();
        String url = "/admin/reports/" + report.getId();

        mockMvc.perform(patch(url).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(url).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(url).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"note\":\"위치가 캠퍼스 밖\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationNote").value("위치가 캠퍼스 밖"));
        mockMvc.perform(patch(url).header("Authorization", bearer(normal))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 비로그인_문의가_저장되고_관리자가_처리한다() throws Exception {
        mockMvc.perform(post("/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"지도에 건물이 안 보여요\",\"contact\":\"a@b.c\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/feedback").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest());

        String body = mockMvc.perform(get("/admin/feedback").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback[0].content").value("지도에 건물이 안 보여요"))
                .andExpect(jsonPath("$.feedback[0].userId").isEmpty())
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));

        mockMvc.perform(patch("/admin/feedback/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    void 로그인_진입시_허용된_redirect_uri만_세션에_남는다() throws Exception {
        String attr = com.hongmap.hongmapbackend.auth.oauth.OAuth2RedirectUriCaptureFilter.SESSION_ATTRIBUTE;

        MockHttpSession allowed = new MockHttpSession();
        mockMvc.perform(get("/oauth2/authorization/kakao").session(allowed)
                        .param("redirect_uri", "https://hongikon.com/admin"))
                .andExpect(status().is3xxRedirection());
        assertThat(allowed.getAttribute(attr)).isEqualTo("https://hongikon.com/admin");

        MockHttpSession webApp = new MockHttpSession();
        mockMvc.perform(get("/oauth2/authorization/kakao").session(webApp)
                        .param("redirect_uri", "https://hongikon.com/auth/callback"))
                .andExpect(status().is3xxRedirection());
        assertThat(webApp.getAttribute(attr)).isEqualTo("https://hongikon.com/auth/callback");

        MockHttpSession evil = new MockHttpSession();
        mockMvc.perform(get("/oauth2/authorization/kakao").session(evil)
                        .param("redirect_uri", "https://evil.example/steal"))
                .andExpect(status().is3xxRedirection());
        assertThat(evil.getAttribute(attr)).isNull();
    }

    @Test
    void 승인된_제보도_새_신고가_임계치에_닿으면_자동숨김되고_다시_공개하면_그_전_신고는_세지_않는다() throws Exception {
        Report report = pendingReport();
        approve(report);

        flagTimes(report, 2);
        assertThat(statusOf(report)).isEqualTo("ACTIVE");
        flagTimes(report, 1); // 승인 뒤 신고 3건 → 운영진 확인 전까지 숨김(이용약관 제8조 4항)
        assertThat(statusOf(report)).isEqualTo("HIDDEN");

        // 운영진이 신고를 보고 다시 공개 — 그 전 신고 3건은 이미 검토한 것이라 세지 않는다
        approve(report);
        flagTimes(report, 2);
        assertThat(statusOf(report)).isEqualTo("ACTIVE");
        flagTimes(report, 1); // 다시 공개한 뒤 새 신고 3건 → 다시 숨김
        assertThat(statusOf(report)).isEqualTo("HIDDEN");

        mockMvc.perform(get("/admin/reports/" + report.getId() + "/flags").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.flags.length()").value(6))
                .andExpect(jsonPath("$.flags[0].reason").value("SPAM"));
    }

    private void approve(Report report) throws Exception {
        mockMvc.perform(patch("/admin/reports/" + report.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
    }

    private void flagTimes(Report report, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            User flagger = userRepository.save(User.builder()
                    .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("신고자" + i).build());
            mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(flagger))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                    .andExpect(status().isCreated());
        }
    }

    private String statusOf(Report report) {
        return reportRepository.findById(report.getId()).orElseThrow().getStatus().name();
    }

    @Test
    void 처리_중_오류는_401로_위장되지_않는다() throws Exception {
        mockMvc.perform(get("/news/abc")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/feedback").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 앱이_보내는_형식_그대로_제보를_만들_수_있다() throws Exception {
        // 앱(ReportComposerModal)은 시각을 toISOString()(끝에 Z)으로, buildingId·floor 를 함께 보낸다.
        String body = """
                {"buildingId": %d, "floor": 1, "lat": 37.55, "lng": 126.925, "category": "ETC",
                 "customCategoryLabel": "플리마켓", "title": "학관 앞 플리마켓", "content": "3시까지",
                 "startsAt": "%s", "endsAt": "%s"}
                """.formatted(building.getId(),
                // 시작은 "지금"(서버는 10분 전까지 받는다). 예전엔 고정 날짜였는데 예정 제보 검증이 생겨 지난 시각은 400 이다.
                java.time.Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString(),
                java.time.Instant.now().plus(java.time.Duration.ofHours(2)).toString());
        mockMvc.perform(post("/reports").header("Authorization", bearer(normal))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customCategoryLabel").value("플리마켓"));
    }
}
