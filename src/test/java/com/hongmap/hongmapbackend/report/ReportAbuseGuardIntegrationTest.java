package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제보 남용·경합 회귀 테스트 (2026-10-05 버그 점검).
 * <ul>
 *   <li>신고: 동시 중복 신고 409, 본인 제보 400, 공개(ACTIVE) 아닌 제보 404, 응답에 전체 신고 수 없음</li>
 *   <li>등록: 좌표·층 범위 400, 건물과 너무 먼 좌표 400, 승인 대기 3건·1시간 5건 넘으면 429</li>
 *   <li>관리자: 반려 → 공개/숨김, 삭제 → 어디로든 400. 숨김 → 다시 공개는 됨</li>
 *   <li>지도 목록: 작성자 N+1 없음, 최대 300건</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportAbuseGuardIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @MockitoSpyBean ReportFlagRepository reportFlagRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManagerFactory entityManagerFactory;

    User author;
    User other;
    User admin;
    Building building;

    @BeforeEach
    void setUp() {
        author = newUser("작성자");
        other = newUser("신고자");
        admin = newUser("관리자");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        building = buildingRepository.save(Building.builder()
                .name("가드관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    /** 같은 H2 DB 를 쓰는 다른 테스트의 지도 목록 개수 단언을 깨지 않게 만든 제보를 지운다. */
    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM report_flags WHERE report_id IN (SELECT id FROM reports WHERE building_id = ?)",
                building.getId());
        jdbcTemplate.update("DELETE FROM reports WHERE building_id = ?", building.getId());
    }

    // ---------- 신고 ----------

    @Test
    void 확인과_INSERT_사이에_같은_신고가_먼저_들어가도_500이_아니라_409() throws Exception {
        Report report = report(author, ReportStatus.ACTIVE);
        flag(report, other).andExpect(status().isCreated()).andExpect(jsonPath("$.flagged").value(true))
                .andExpect(jsonPath("$.flagCount").doesNotExist());

        // 경합 재현: 두 번째 요청의 확인은 "아직 없음"을 본다(동시에 들어온 첫 요청이 그 뒤 커밋된 상황).
        Answer<?> real = mockingDetails(reportFlagRepository).getMockCreationSettings().getDefaultAnswer();
        doReturn(false).doAnswer(real).when(reportFlagRepository).existsByReportIdAndUserId(anyLong(), anyLong());

        flag(report, other).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ReportService.ALREADY_FLAGGED_MESSAGE));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report_flags WHERE report_id = ?",
                Long.class, report.getId())).isEqualTo(1L);
    }

    @Test
    void 본인_제보는_신고할_수_없고_공개되지_않은_제보는_404() throws Exception {
        flag(report(author, ReportStatus.ACTIVE), author).andExpect(status().isBadRequest());
        for (ReportStatus hiddenStatus : List.of(ReportStatus.PENDING, ReportStatus.REJECTED, ReportStatus.HIDDEN,
                ReportStatus.DELETED)) {
            flag(report(author, hiddenStatus), other).andExpect(status().isNotFound());
        }
    }

    // ---------- 등록 ----------

    @Test
    void 좌표_층_범위를_벗어나거나_건물에서_멀면_400이고_DB_오류가_나지_않는다() throws Exception {
        create(author, "1000", "126.925", 1).andExpect(status().isBadRequest());     // DECIMAL(10,7) 초과 → 예전엔 500
        create(author, "37.55", "-181", 1).andExpect(status().isBadRequest());
        create(author, "37.55", "126.925", 31).andExpect(status().isBadRequest());
        create(author, "37.55", "126.925", -11).andExpect(status().isBadRequest());
        create(author, "37.55", "126.925", 0).andExpect(status().isBadRequest());
        // 건물 중심에서 약 450m 북쪽(위도 0.004도)
        create(author, "37.554", "126.925", 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("제보 위치가 고른 건물에서 너무 멀어요. 건물 가까이로 옮겨 주세요."));
        // 약 220m(앱 기준 200m + 좌표 차이 여유) 안쪽은 받는다. 소수 7자리 넘는 좌표는 맞춰 저장.
        create(author, "37.552", "126.925", -2).andExpect(status().isCreated());
        create(author, "37.550000049", "126.92500001", 30).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lat").value(37.55));
    }

    @Test
    void 승인_대기가_3건이면_더_올릴_수_없고_승인되면_다시_올릴_수_있다() throws Exception {
        for (int i = 0; i < 3; i++) {
            create(author, "37.55", "126.925", 1).andExpect(status().isCreated());
        }
        create(author, "37.55", "126.925", 1).andExpect(status().isTooManyRequests());

        jdbcTemplate.update("UPDATE reports SET status = 'ACTIVE' WHERE user_id = ?", author.getId());
        create(author, "37.55", "126.925", 1).andExpect(status().isCreated());
    }

    @Test
    void 한_시간에_5건을_넘게_올리면_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            create(author, "37.55", "126.925", 1).andExpect(status().isCreated());
            // 승인 대기 한도와 따로 보려고 바로 승인 상태로 바꾼다.
            jdbcTemplate.update("UPDATE reports SET status = 'ACTIVE' WHERE user_id = ?", author.getId());
        }
        create(author, "37.55", "126.925", 1).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("제보를 너무 자주 올리고 있어요. 잠시 후 다시 시도해 주세요."));
        // 다른 사용자는 영향 없음
        create(other, "37.55", "126.925", 1).andExpect(status().isCreated());
    }

    // ---------- 관리자 상태 변경 ----------

    @Test
    void 반려_삭제한_제보는_다시_공개할_수_없고_숨김은_다시_공개된다() throws Exception {
        Report rejected = report(author, ReportStatus.REJECTED);
        moderate(rejected, "ACTIVE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "반려·삭제한 제보는 다시 공개할 수 없어요. 작성자에게 다시 올려 달라고 해 주세요."));
        moderate(rejected, "HIDDEN").andExpect(status().isBadRequest());
        // 반려 사유 고치기·반려 → 삭제는 된다.
        moderate(rejected, "REJECTED", "사유 수정").andExpect(status().isOk());
        moderate(rejected, "DELETED").andExpect(status().isOk());

        Report deleted = report(author, ReportStatus.DELETED);
        for (String target : List.of("ACTIVE", "HIDDEN", "REJECTED", "DELETED")) {
            moderate(deleted, target, "메모").andExpect(status().isBadRequest());
        }

        Report hidden = report(author, ReportStatus.HIDDEN);
        moderate(hidden, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ---------- 지도 목록 ----------

    @Test
    void 지도_목록은_작성자를_한_번에_읽고_최대_300건이다() throws Exception {
        List<User> authors = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            authors.add(newUser("목록" + i));
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < ReportService.LIVE_LIST_LIMIT + 5; i++) {
            rows.add(new Object[]{authors.get(i % authors.size()).getId(), building.getId(), "제보" + i,
                    Timestamp.valueOf(now.minusHours(1)), Timestamp.valueOf(now.plusHours(2)),
                    Timestamp.valueOf(now.minusSeconds(i))});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO reports (user_id, building_id, floor, lat, lng, category, title, starts_at, ends_at, status,
                                     admin_reminder_count, created_at, updated_at)
                VALUES (?, ?, 1, 37.55, 126.925, 'EVENT', ?, ?, ?, 'ACTIVE', 0, ?, CURRENT_TIMESTAMP)
                """, rows);

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            mockMvc.perform(get("/reports").param("live", "true").param("buildingId", building.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reports.length()").value(ReportService.LIVE_LIST_LIMIT))
                    .andExpect(jsonPath("$.reports[0].title").value("제보0"));
            // 제보 1번 + 사진 묶음(@BatchSize 100 → 300건이면 3번). 작성자를 제보마다 읽으면 여기서 5번 이상 더 나온다.
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }

    // ---------- helpers ----------

    private User newUser(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private Report report(User owner, ReportStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return reportRepository.save(Report.builder()
                .user(owner).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .status(status)
                .build());
    }

    private org.springframework.test.web.servlet.ResultActions flag(Report report, User user) throws Exception {
        return mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions create(User user, String lat, String lng, int floor)
            throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String body = """
                {"buildingId":%d,"floor":%d,"lat":%s,"lng":%s,"category":"EVENT","title":"가드 테스트",
                 "startsAt":"%s","endsAt":"%s"}
                """.formatted(building.getId(), floor, lat, lng, now.minusMinutes(1), now.plusHours(2));
        return mockMvc.perform(post("/reports").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions moderate(Report report, String target) throws Exception {
        return moderate(report, target, null);
    }

    private org.springframework.test.web.servlet.ResultActions moderate(Report report, String target, String note)
            throws Exception {
        String body = note == null ? "{\"status\":\"" + target + "\"}"
                : "{\"status\":\"" + target + "\",\"note\":\"" + note + "\"}";
        return mockMvc.perform(patch("/admin/reports/" + report.getId()).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
