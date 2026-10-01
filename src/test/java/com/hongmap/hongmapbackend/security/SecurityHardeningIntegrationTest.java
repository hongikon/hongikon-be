package com.hongmap.hongmapbackend.security;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 점검(2026-10)에서 고친 동작: 공개 제보 응답의 작성자 익명화, 401 응답의 세션 미생성, 관리자 접속 기록.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SecurityHardeningIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    User author;
    User other;
    Building building;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("홍길동실명").build());
        other = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname("다른학생").build());
        building = buildingRepository.save(Building.builder()
                .name("보안관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        LocalDateTime now = LocalDateTime.now();
        reportRepository.save(Report.builder()
                .user(author).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .status(ReportStatus.ACTIVE)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    @Test
    void 비로그인_제보_목록에는_작성자_닉네임이_나가지_않는다() throws Exception {
        mockMvc.perform(get("/reports").param("buildingId", building.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].authorNickname").value("익명"))
                .andExpect(jsonPath("$.reports[*].authorNickname", not(hasItem("홍길동실명"))));
    }

    @Test
    void 다른_사용자에게도_작성자_닉네임이_나가지_않는다() throws Exception {
        mockMvc.perform(get("/reports").param("buildingId", building.getId().toString())
                        .header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].authorNickname").value("익명"))
                .andExpect(jsonPath("$.reports[0].isMine").value(false));
    }

    @Test
    void 본인_제보에는_본인_닉네임이_보인다() throws Exception {
        mockMvc.perform(get("/reports").param("buildingId", building.getId().toString())
                        .header("Authorization", bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].authorNickname").value("홍길동실명"))
                .andExpect(jsonPath("$.reports[0].isMine").value(true));
    }

    @Test
    void 인증_실패_응답은_세션을_만들지_않는다() throws Exception {
        MvcResult result = mockMvc.perform(get("/users/me/bookmarks"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void 관리자_요청은_접속기록을_남긴다(CapturedOutput output) throws Exception {
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", other.getId());
        mockMvc.perform(get("/admin/feedback").header("Authorization", bearer(other)))
                .andExpect(status().isOk());
        assertThat(output.getOut())
                .contains("admin-access actor=user:" + other.getId())
                .contains("method=GET path=/admin/feedback status=200");
    }
}
