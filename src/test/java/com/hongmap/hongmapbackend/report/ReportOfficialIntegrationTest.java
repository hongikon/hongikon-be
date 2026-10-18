package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공식 계정 제보 (10-19 요청).
 * <ul>
 *   <li>등록 한도: 일반 회원은 승인 대기 3건·1시간 5건, 공식 계정은 승인 대기 20건·1시간 30건</li>
 *   <li>시간 수정(PUT /reports/{id}/schedule): 공식 계정 작성자만, 승인 대기·공개 제보만</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportOfficialIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    User official;
    User member;
    Building building;

    @BeforeEach
    void setUp() {
        official = newUser("학생회");
        jdbcTemplate.update("UPDATE users SET official_name = ? WHERE id = ?",
                "테스트 학생회 " + UUID.randomUUID().toString().substring(0, 6), official.getId());
        member = newUser("일반");
        building = buildingRepository.save(Building.builder()
                .name("공식관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM reports WHERE building_id = ?", building.getId());
    }

    // ---------- 등록 한도 ----------

    @Test
    void 공식_계정은_승인_대기_3건을_넘어도_올릴_수_있다() throws Exception {
        for (int i = 0; i < 6; i++) {
            create(official).andExpect(status().isCreated());
        }
        for (int i = 0; i < 3; i++) {
            create(member).andExpect(status().isCreated());
        }
        create(member).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("승인을 기다리는 제보가 3건 있어요. 검토가 끝난 뒤 다시 올려 주세요."));
    }

    @Test
    void 공식_계정은_한_시간에_5건을_넘게_올릴_수_있다() throws Exception {
        for (int i = 0; i < 8; i++) {
            create(official).andExpect(status().isCreated());
            jdbcTemplate.update("UPDATE reports SET status = 'ACTIVE' WHERE user_id = ?", official.getId());
        }
    }

    // ---------- 시간 수정 ----------

    @Test
    void 공식_계정은_이미_시작한_제보의_종료_시각을_늦출_수_있다() throws Exception {
        Report report = report(official, ReportStatus.ACTIVE);
        LocalDateTime newEnd = report.getEndsAt().plusHours(2);

        schedule(report, official, report.getStartsAt(), newEnd).andExpect(status().isOk())
                .andExpect(jsonPath("$.endsAt").value(newEnd.toString()));
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getEndsAt()).isEqualTo(newEnd);
    }

    @Test
    void 공식_계정은_승인_대기_제보의_시작_시각을_미룰_수_있다() throws Exception {
        Report report = report(official, ReportStatus.PENDING);
        LocalDateTime start = now().plusDays(1);

        schedule(report, official, start, start.plusHours(3)).andExpect(status().isOk());
        Report saved = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(saved.getStartsAt()).isEqualTo(start);
        assertThat(saved.getEndsAt()).isEqualTo(start.plusHours(3));
    }

    @Test
    void 일반_회원이나_남의_제보는_시간을_고칠_수_없다() throws Exception {
        Report mine = report(member, ReportStatus.ACTIVE);
        schedule(mine, member, mine.getStartsAt(), mine.getEndsAt().plusHours(1)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("시간 수정은 공식 계정만 할 수 있어요."));

        Report others = report(member, ReportStatus.ACTIVE);
        schedule(others, official, others.getStartsAt(), others.getEndsAt().plusHours(1)).andExpect(status().isForbidden());
    }

    @Test
    void 규칙에_어긋난_시각이나_반려된_제보는_고칠_수_없다() throws Exception {
        Report report = report(official, ReportStatus.ACTIVE);
        // 종료가 시작보다 앞
        schedule(report, official, report.getStartsAt(), report.getStartsAt().minusMinutes(5))
                .andExpect(status().isBadRequest());
        // 시작을 바꾸면서 이미 지난 시각으로
        schedule(report, official, now().minusHours(3), now().plusHours(1)).andExpect(status().isBadRequest());

        Report rejected = report(official, ReportStatus.REJECTED);
        schedule(rejected, official, rejected.getStartsAt(), rejected.getEndsAt().plusHours(1))
                .andExpect(status().isConflict());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private User newUser(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private Report report(User owner, ReportStatus status) {
        LocalDateTime now = now();
        return reportRepository.save(Report.builder()
                .user(owner).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.EVENT).title("축제 부스")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .status(status)
                .build());
    }

    private ResultActions create(User user) throws Exception {
        LocalDateTime now = now();
        String body = """
                {"buildingId":%d,"floor":1,"lat":37.55,"lng":126.925,"category":"EVENT","title":"공식 테스트",
                 "startsAt":"%s","endsAt":"%s"}
                """.formatted(building.getId(), now.minusMinutes(1), now.plusHours(2));
        return mockMvc.perform(post("/reports").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions schedule(Report report, User user, LocalDateTime startsAt, LocalDateTime endsAt)
            throws Exception {
        String body = """
                {"startsAt":"%s","endsAt":"%s"}
                """.formatted(startsAt, endsAt);
        return mockMvc.perform(put("/reports/" + report.getId() + "/schedule").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
