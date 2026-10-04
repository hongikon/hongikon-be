package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.auth.oauth.KakaoUnlinkClient;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.report.AuthorKeys;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.report.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * App Store 가이드라인 1.2(UGC): 작성자 authorKey, 신고 사유, 관리자 이용 정지, 탈퇴 시 카카오 연결 끊기.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserModerationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean KakaoUnlinkClient kakaoUnlinkClient;

    User admin;
    User author;
    User other;
    Building building;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM report_flags");
        jdbcTemplate.update("DELETE FROM reports");
        admin = newUser("관리자");
        author = newUser("작성자");
        other = newUser("다른학생");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        building = buildingRepository.save(Building.builder()
                .name("테스트관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    private User newUser(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private Report activeReport(User user) {
        LocalDateTime now = LocalDateTime.now();
        return reportRepository.save(Report.builder()
                .user(user).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .status(ReportStatus.ACTIVE)
                .build());
    }

    private String reportBody() {
        LocalDateTime now = LocalDateTime.now();
        return """
                {"buildingId":%d,"floor":1,"lat":37.55,"lng":126.925,"category":"FOOD_TRUCK",
                 "title":"붕어빵","startsAt":"%s","endsAt":"%s"}
                """.formatted(building.getId(), now.plusMinutes(1).withNano(0), now.plusHours(2).withNano(0));
    }

    @Test
    void 공개_제보에는_작성자_id_대신_authorKey가_실린다() throws Exception {
        activeReport(author);
        String expectedKey = AuthorKeys.of(author.getId());
        assertThat(expectedKey).hasSize(16);

        mockMvc.perform(get("/reports"))
                .andExpect(jsonPath("$.reports[0].authorKey").value(expectedKey))
                .andExpect(jsonPath("$.reports[0].isMine").value(false))
                .andExpect(jsonPath("$.reports[0].authorId").doesNotExist())
                .andExpect(jsonPath("$.reports[0].userId").doesNotExist());
        mockMvc.perform(get("/reports").header("Authorization", bearer(author)))
                .andExpect(jsonPath("$.reports[0].isMine").value(true));
        mockMvc.perform(get("/reports").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.reports[0].isMine").value(false))
                .andExpect(jsonPath("$.reports[0].authorKey").value(expectedKey));
    }

    @Test
    void 신고_사유는_개인정보_노출을_받고_모르는_값은_400() throws Exception {
        Report report = activeReport(author);
        mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"PRIVACY\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"WHATEVER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 관리자는_앱에_보이는_앱_닉네임으로도_회원을_찾는다() throws Exception {
        String appName = "붕어빵왕" + UUID.randomUUID().toString().substring(0, 4);
        jdbcTemplate.update("UPDATE users SET app_nickname = ? WHERE id = ?", appName, author.getId());

        mockMvc.perform(get("/admin/users").param("q", appName.substring(0, 6)).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[?(@.id == " + author.getId() + ")].displayName").value(appName))
                .andExpect(jsonPath("$.users[?(@.id == " + author.getId() + ")].nickname").value("작성자"));
        // 로그인 닉네임으로도 그대로 찾는다
        mockMvc.perform(get("/admin/users").param("q", "다른학").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.users[?(@.id == " + other.getId() + ")].displayName").value("다***"));
    }

    @Test
    void 정지된_사용자는_제보_신고_문의가_403이고_조회는_된다_해제하면_다시_된다() throws Exception {
        Report report = activeReport(other);

        mockMvc.perform(post("/admin/users/" + author.getId() + "/suspend").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"욕설 반복\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspendedReason").value("욕설 반복"));

        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(reportBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(SuspendedUserInterceptor.suspendedMessage("욕설 반복")));
        mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/feedback").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"문의\"}"))
                .andExpect(status().isForbidden());
        // 로그인 상태 조회는 그대로
        mockMvc.perform(get("/reports").header("Authorization", bearer(author))).andExpect(status().isOk());

        mockMvc.perform(get("/admin/users").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.users[?(@.id == " + author.getId() + ")].status").value("SUSPENDED"));

        mockMvc.perform(post("/admin/users/" + author.getId() + "/unsuspend").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.suspendedReason").isEmpty());
        mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/reports").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(reportBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void 정지_API는_관리자만_쓰고_사유가_필요하며_관리자는_정지할_수_없다() throws Exception {
        mockMvc.perform(post("/admin/users/" + author.getId() + "/suspend").header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/" + author.getId() + "/suspend").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/users/" + admin.getId() + "/suspend").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/users/999999/suspend").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 관리자는_다른_회원을_관리자로_지정하고_해제할_수_있다() throws Exception {
        mockMvc.perform(post("/admin/users/" + other.getId() + "/grant-admin").header("Authorization", bearer(author)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/" + other.getId() + "/grant-admin").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        // 새 관리자는 바로 관리자 API 를 쓸 수 있다(역할은 요청마다 DB 에서 읽는다).
        mockMvc.perform(get("/admin/users").header("Authorization", bearer(other)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/users/" + other.getId() + "/revoke-admin").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
        mockMvc.perform(get("/admin/users").header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 자기_자신은_해제할_수_없고_정지된_회원은_지정할_수_없다() throws Exception {
        mockMvc.perform(post("/admin/users/" + admin.getId() + "/revoke-admin").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/admin/users/" + author.getId() + "/suspend").header("Authorization", bearer(admin))
                        .contentType("application/json").content("{\"reason\":\"스팸\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/users/" + author.getId() + "/grant-admin").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 회원_조회는_id나_닉네임으로_찾는다() throws Exception {
        mockMvc.perform(get("/admin/users").param("q", String.valueOf(author.getId())).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.users[0].nickname").value("작성자"))
                .andExpect(jsonPath("$.users[0].status").value("ACTIVE"));
        mockMvc.perform(get("/admin/users").param("q", "다른").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.users[0].id").value(other.getId()))
                .andExpect(jsonPath("$.users[0].email").doesNotExist());
        mockMvc.perform(get("/admin/users/" + other.getId()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.nickname").value(not("작성자")));
    }

    @Test
    void 카카오_회원이_탈퇴하면_연결_끊기를_요청한다() throws Exception {
        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());
        verify(kakaoUnlinkClient).unlinkAfterCommit(author.getSocialId());
        assertThat(userRepository.findById(author.getId())).isEmpty();
    }

    @Test
    void 카카오가_아닌_회원은_연결_끊기를_요청하지_않는다() throws Exception {
        User google = userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.GOOGLE).nickname("구글").build());
        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(google)))
                .andExpect(status().isNoContent());
        verify(kakaoUnlinkClient, never()).unlinkAfterCommit(anyString());
    }
}
