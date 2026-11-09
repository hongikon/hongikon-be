package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 작성자의 제보 수정(PUT /reports/{id}, 11-09): 공개 제보도 바로 반영되고 editedAt 이 남는다, 남의 제보·숨김 제보는 못 고친다,
 * 시작 시각을 그대로 두면 이미 시작한 제보도 고친다, 사진은 남길 번호대로 다시 맞춘다. H2, 서버 시각 UTC.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportOwnerEditIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    User author;
    User other;
    Building building;
    LocalDateTime now;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("학생").build());
        other = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("다른 학생").build());
        building = buildingRepository.save(Building.builder()
                .name("수정관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void 공개된_제보를_고치면_상태는_그대로이고_바로_바뀌며_수정_시각이_남는다() throws Exception {
        Report report = save(ReportStatus.ACTIVE, now.minusHours(1), now.plusHours(2));

        edit(author, report, body(now.minusHours(1), now.plusHours(5), 3, "FOOD_TRUCK", null, "고친 제목", "고친 내용", "T동 앞", "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.title").value("고친 제목"))
                .andExpect(jsonPath("$.content").value("고친 내용"))
                .andExpect(jsonPath("$.floor").value(3))
                .andExpect(jsonPath("$.placeLabel").value("T동 앞"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());

        Report saved = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.ACTIVE);
        assertThat(saved.getEndsAt()).isEqualTo(now.plusHours(5));
        assertThat(saved.getEditedAt()).isNotNull();
    }

    @Test
    void 남의_제보는_403_숨김_제보는_409() throws Exception {
        Report report = save(ReportStatus.ACTIVE, now.plusHours(1), now.plusHours(2));
        edit(other, report, body(now.plusHours(1), now.plusHours(2), 1, "FOOD_TRUCK", null, "남이 고침", null, null, "[]"))
                .andExpect(status().isForbidden());

        Report hidden = save(ReportStatus.HIDDEN, now.plusHours(1), now.plusHours(2));
        edit(author, hidden, body(now.plusHours(1), now.plusHours(2), 1, "FOOD_TRUCK", null, "숨김", null, null, "[]"))
                .andExpect(status().isConflict());
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getTitle()).isEqualTo("원래 제목");
    }

    @Test
    void 시작을_지난_시각으로_바꾸면_400_시작을_그대로_두면_받는다() throws Exception {
        Report report = save(ReportStatus.ACTIVE, now.minusHours(2), now.plusHours(1));
        edit(author, report, body(now.minusHours(1), now.plusHours(1), 1, "FOOD_TRUCK", null, "제목", null, null, "[]"))
                .andExpect(status().isBadRequest());
        edit(author, report, body(now.minusHours(2), now.plusHours(3), 1, "FOOD_TRUCK", null, "제목", null, null, "[]"))
                .andExpect(status().isOk());
    }

    @Test
    void 층과_카테고리_규칙은_등록과_같다() throws Exception {
        Report report = save(ReportStatus.PENDING, now.plusHours(1), now.plusHours(2));
        edit(author, report, body(now.plusHours(1), now.plusHours(2), 0, "FOOD_TRUCK", null, "제목", null, null, "[]"))
                .andExpect(status().isBadRequest());
        edit(author, report, body(now.plusHours(1), now.plusHours(2), 1, "FOOD_TRUCK", "직접", "제목", null, null, "[]"))
                .andExpect(status().isBadRequest());
        edit(author, report, body(now.plusHours(1), now.plusHours(2), 1, "ETC", "붕어빵", "제목", null, null, "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customCategoryLabel").value("붕어빵"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 사진은_남길_번호_순서대로_다시_맞추고_뺀_사진은_연결을_끊는다() throws Exception {
        Report report = save(ReportStatus.ACTIVE, now.plusHours(1), now.plusHours(2));
        String prefix = "reports/" + UUID.randomUUID();
        report.addImages(List.of(prefix + "-a.jpg", prefix + "-b.jpg", prefix + "-c.jpg"));
        reportRepository.saveAndFlush(report);

        edit(author, report, body(now.plusHours(1), now.plusHours(2), 1, "FOOD_TRUCK", null, "제목", null, null, "[2, 0]"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForList(
                "SELECT image_key FROM report_images WHERE report_id = ? ORDER BY sort_order", String.class, report.getId()))
                .containsExactly(prefix + "-c.jpg", prefix + "-a.jpg");

        // 없는 번호·겹친 번호는 400
        edit(author, report, body(now.plusHours(1), now.plusHours(2), 1, "FOOD_TRUCK", null, "제목", null, null, "[2]"))
                .andExpect(status().isBadRequest());
        edit(author, report, body(now.plusHours(1), now.plusHours(2), 1, "FOOD_TRUCK", null, "제목", null, null, "[0, 0]"))
                .andExpect(status().isBadRequest());
    }

    private Report save(ReportStatus status, LocalDateTime startsAt, LocalDateTime endsAt) {
        return reportRepository.save(Report.builder()
                .user(author).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("원래 제목")
                .startsAt(startsAt).endsAt(endsAt).status(status).build());
    }

    private static String body(LocalDateTime startsAt, LocalDateTime endsAt, int floor, String category, String customLabel,
                               String title, String content, String placeLabel, String keepIndexes) {
        return """
                {"floor":%d,"category":"%s","customCategoryLabel":%s,"title":"%s","content":%s,"placeLabel":%s,
                 "keepImageIndexes":%s,"startsAt":"%sZ","endsAt":"%sZ"}
                """.formatted(floor, category, json(customLabel), title, json(content), json(placeLabel), keepIndexes,
                startsAt, endsAt);
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private ResultActions edit(User user, Report report, String body) throws Exception {
        return mockMvc.perform(put("/reports/" + report.getId())
                .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
