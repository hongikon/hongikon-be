package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공개 회원 번호(K7Q2M9XA4D): 가입 시 발급, 내 번호 조회, 관리자 검색, 공개 응답에는 싣지 않기. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberCodeIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    User admin;
    User member;

    @BeforeEach
    void setUp() {
        admin = newUser("관리자");
        member = newUser("회원번호학생-" + UUID.randomUUID().toString().substring(0, 6));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    private User newUser(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    @Test
    void 가입하면_무작위_회원_번호가_붙고_서로_다르다() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            User user = newUser("학생" + i);
            assertThat(user.getMemberCode()).matches("[A-Z0-9]{10}");
            codes.add(user.getMemberCode());
        }
        assertThat(codes).hasSize(30);
        String stored = jdbcTemplate.queryForObject("SELECT member_code FROM users WHERE id = ?", String.class, member.getId());
        assertThat(stored).isEqualTo(member.getMemberCode());
    }

    @Test
    void 같은_회원_번호는_DB가_막는다() {
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE users SET member_code = ? WHERE id = ?",
                admin.getMemberCode(), member.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 내_회원_번호를_조회한다() throws Exception {
        mockMvc.perform(get("/users/me/member-code").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCode").value(member.getMemberCode()));
        mockMvc.perform(get("/users/me/member-code"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자는_회원_번호로_찾는다_대소문자_무관() throws Exception {
        String code = member.getMemberCode();
        for (String q : new String[]{code, code.toLowerCase(Locale.ROOT), " " + code.toLowerCase(Locale.ROOT) + " "}) {
            mockMvc.perform(get("/admin/users").param("q", q).header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.users[*].id", hasItem(member.getId().intValue())))
                    .andExpect(jsonPath("$.users[?(@.id == " + member.getId() + ")].memberCode").value(code));
        }
        // id·닉네임 검색도 그대로 되고, 응답에 memberCode 가 실린다
        mockMvc.perform(get("/admin/users").param("q", String.valueOf(member.getId())).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.users[0].id").value(member.getId()))
                .andExpect(jsonPath("$.users[0].memberCode").value(code));
        mockMvc.perform(get("/admin/users").param("q", member.getNickname()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.users[0].memberCode").value(code));
        mockMvc.perform(get("/admin/users/" + member.getId()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.memberCode").value(code));
        // 없는 번호
        mockMvc.perform(get("/admin/users").param("q", "ZZZZZZZZZZ").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.users").isEmpty());
    }

    @Test
    void 공개_제보_응답에는_회원_번호가_없다() throws Exception {
        jdbcTemplate.update("DELETE FROM report_flags");
        jdbcTemplate.update("DELETE FROM reports");
        Building building = buildingRepository.save(Building.builder()
                .name("회원번호관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        LocalDateTime now = LocalDateTime.now();
        Report report = reportRepository.save(Report.builder()
                .user(member).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .status(ReportStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/reports").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].id").value(report.getId()))
                .andExpect(jsonPath("$.reports[0].authorKey").exists())
                .andExpect(content().string(not(containsString(member.getMemberCode()))))
                .andExpect(content().string(not(containsString("memberCode"))));
    }
}
