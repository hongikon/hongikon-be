package com.hongmap.hongmapbackend.admin;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.feedback.Feedback;
import com.hongmap.hongmapbackend.feedback.FeedbackRepository;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 콘솔 로그인 닉네임 가리기(개인정보 보호법 제3조 최소 처리).
 * <ul>
 *   <li>관리자 응답(제보·신고·문의·회원)에 로그인 닉네임 원문이 없다 — 앱에 보이는 이름 + 회원 번호만</li>
 *   <li>GET /admin/users/{id}/login-name 은 관리자 전용이고, 부를 때마다 admin_pii_access_logs 에 기록이 남는다</li>
 *   <li>회원 조회는 로그인 닉네임으로 찾지 않는다(회원 번호·id·앱 닉네임만)</li>
 *   <li>열람 기록은 보관 기간(최소 1년)이 지나야 지운다</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPiiAccessIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired FeedbackRepository feedbackRepository;
    @Autowired AdminPiiAccessLogRepository accessLogRepository;
    @Autowired AdminPiiAccessLogPurger purger;
    @Autowired JdbcTemplate jdbcTemplate;

    User admin;
    User author;
    User flagger;
    /** 작성자의 로그인 닉네임 원문 — 다른 테스트 데이터와 겹치지 않게 무작위 꼬리를 붙인다. 응답 어디에도 나오면 안 된다. */
    String loginName;
    String flaggerLoginName;
    Building building;

    @BeforeEach
    void setUp() {
        String tail = UUID.randomUUID().toString().substring(0, 6);
        loginName = "홍길동" + tail;
        flaggerLoginName = "김신고" + tail;
        admin = newUser("운영자" + tail, SocialType.KAKAO);
        author = newUser(loginName, SocialType.APPLE);
        flagger = newUser(flaggerLoginName, SocialType.KAKAO);
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        building = buildingRepository.save(Building.builder()
                .name("열람기록관-" + tail)
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
    }

    private User newUser(String nickname, SocialType socialType) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(socialType).nickname(nickname).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private Report pendingReport(User user) {
        LocalDateTime now = LocalDateTime.now();
        return reportRepository.save(Report.builder()
                .user(user).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .build());
    }

    private List<AdminPiiAccessLog> logsOf(User target) {
        return accessLogRepository.findByTargetUserIdOrderByIdDesc(target.getId());
    }

    @Test
    void 관리자_제보_목록_상태변경_신고목록에_로그인_닉네임이_없고_회원번호가_있다() throws Exception {
        Report report = pendingReport(author);

        mockMvc.perform(get("/admin/reports").param("status", "ALL").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[?(@.id == " + report.getId() + ")].authorDisplayName").value("홍********"))
                .andExpect(jsonPath("$.reports[?(@.id == " + report.getId() + ")].authorMemberCode").value(author.getMemberCode()))
                // 구버전 화면 호환 키도 가린 이름이다
                .andExpect(jsonPath("$.reports[?(@.id == " + report.getId() + ")].authorNickname").value("홍********"))
                .andExpect(content().string(not(containsString(loginName))));

        // 승인 뒤 신고 — 신고 목록의 신고자도 가린 이름 + 회원 번호
        mockMvc.perform(patch("/admin/reports/" + report.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorMemberCode").value(author.getMemberCode()))
                .andExpect(content().string(not(containsString(loginName))));
        mockMvc.perform(post("/reports/" + report.getId() + "/flags").header("Authorization", bearer(flagger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/admin/reports/" + report.getId() + "/flags").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0].reporterId").value(flagger.getId()))
                .andExpect(jsonPath("$.flags[0].reporterDisplayName").value("김********"))
                .andExpect(jsonPath("$.flags[0].reporterNickname").value("김********"))
                .andExpect(jsonPath("$.flags[0].reporterMemberCode").value(flagger.getMemberCode()))
                .andExpect(content().string(not(containsString(flaggerLoginName))));
    }

    @Test
    void 관리자_문의_목록에_작성자_로그인_닉네임이_없다() throws Exception {
        feedbackRepository.save(new Feedback(author, "지도에 건물이 안 보여요 " + loginName.substring(3), null));
        mockMvc.perform(get("/admin/feedback").param("status", "ALL").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback[?(@.userId == " + author.getId() + ")].userDisplayName").value("홍********"))
                .andExpect(jsonPath("$.feedback[?(@.userId == " + author.getId() + ")].userNickname").value("홍********"))
                .andExpect(jsonPath("$.feedback[?(@.userId == " + author.getId() + ")].userMemberCode").value(author.getMemberCode()))
                .andExpect(content().string(not(containsString(loginName))));
    }

    @Test
    void 회원_조회는_앱_닉네임_회원번호_id로_찾고_로그인_닉네임으로는_못_찾는다() throws Exception {
        String appName = "와우" + UUID.randomUUID().toString().substring(0, 4);
        jdbcTemplate.update("UPDATE users SET app_nickname = ? WHERE id = ?", appName, author.getId());

        // 로그인 닉네임(전체·일부) → 없음
        for (String q : new String[]{loginName, loginName.substring(0, 5)}) {
            mockMvc.perform(get("/admin/users").param("q", q).header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.users[*].id", not(hasItem(author.getId().intValue()))));
        }
        // 앱 닉네임 일부 → 앱 닉네임이 카드 제목으로 쓰일 수 있게 appNickname·displayName 이 실린다
        mockMvc.perform(get("/admin/users").param("q", appName.substring(0, 4)).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.users[?(@.id == " + author.getId() + ")].appNickname").value(appName))
                .andExpect(jsonPath("$.users[?(@.id == " + author.getId() + ")].displayName").value(appName))
                .andExpect(content().string(not(containsString(loginName))));
        // 회원 번호(소문자도) · id
        for (String q : new String[]{author.getMemberCode().toLowerCase(Locale.ROOT), String.valueOf(author.getId())}) {
            mockMvc.perform(get("/admin/users").param("q", q).header("Authorization", bearer(admin)))
                    .andExpect(jsonPath("$.users[0].id").value(author.getId()))
                    .andExpect(jsonPath("$.users[0].memberCode").value(author.getMemberCode()))
                    .andExpect(content().string(not(containsString(loginName))));
        }
        mockMvc.perform(get("/admin/users/" + author.getId()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.appNickname").value(appName))
                .andExpect(content().string(not(containsString(loginName))));
    }

    @Test
    void 로그인_닉네임_열람은_관리자만_되고_열람할_때마다_기록이_남는다() throws Exception {
        String url = "/admin/users/" + author.getId() + "/login-name";

        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).header("Authorization", bearer(flagger))).andExpect(status().isForbidden());
        assertThat(logsOf(author)).isEmpty();

        mockMvc.perform(get(url).param("purpose", "  신고 3건 — 동일인 여부 확인 ").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.userId").value(author.getId()))
                .andExpect(jsonPath("$.loginNickname").value(loginName))
                .andExpect(jsonPath("$.socialType").value("APPLE"))
                .andExpect(jsonPath("$.accessedAt").isNotEmpty());

        List<AdminPiiAccessLog> logs = logsOf(author);
        assertThat(logs).hasSize(1);
        AdminPiiAccessLog log = logs.get(0);
        assertThat(log.getAdminUserId()).isEqualTo(admin.getId());
        assertThat(log.getTargetUserId()).isEqualTo(author.getId());
        assertThat(log.getField()).isEqualTo(AdminPiiAccessLog.FIELD_LOGIN_NICKNAME);
        assertThat(log.getPurpose()).isEqualTo("신고 3건 — 동일인 여부 확인");
        assertThat(log.getAccessedAt()).isNotNull();

        // 다시 열람하면 또 남는다(사유 없이도 된다)
        mockMvc.perform(get(url).header("Authorization", bearer(admin))).andExpect(status().isOk());
        assertThat(logsOf(author)).hasSize(2);
        assertThat(logsOf(author).get(0).getPurpose()).isNull();
    }

    @Test
    void 없는_회원의_로그인_닉네임_열람은_404이고_기록이_남지_않는다() throws Exception {
        long missing = 987_654_321L;
        mockMvc.perform(get("/admin/users/" + missing + "/login-name").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        assertThat(accessLogRepository.findByTargetUserIdOrderByIdDesc(missing)).isEmpty();
    }

    @Test
    void 열람_사유는_100자에서_자른다() {
        assertThat(AdminPiiAccessService.normalizePurpose(null)).isNull();
        assertThat(AdminPiiAccessService.normalizePurpose("   ")).isNull();
        assertThat(AdminPiiAccessService.normalizePurpose("가".repeat(150))).hasSize(100);
    }

    @Test
    void 열람_기록은_보관_기간이_지나야_지우고_1년_미만으로는_지우지_않는다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 4, 30);
        AdminPiiAccessLog old = accessLogRepository.save(new AdminPiiAccessLog(admin.getId(), author.getId(),
                AdminPiiAccessLog.FIELD_LOGIN_NICKNAME, null, now.minusDays(731)));
        AdminPiiAccessLog recent = accessLogRepository.save(new AdminPiiAccessLog(admin.getId(), author.getId(),
                AdminPiiAccessLog.FIELD_LOGIN_NICKNAME, null, now.minusDays(400)));

        purger.purgeOlderThan(now, 730);
        assertThat(accessLogRepository.existsById(old.getId())).isFalse();
        assertThat(accessLogRepository.existsById(recent.getId())).isTrue();

        // 설정을 30일로 잘못 줄여도 1년(365일) 안의 기록은 남는다
        assertThat(AdminPiiAccessLogPurger.effectiveRetentionDays(30)).isEqualTo(AdminPiiAccessLogPurger.MIN_RETENTION_DAYS);
        AdminPiiAccessLog withinYear = accessLogRepository.save(new AdminPiiAccessLog(admin.getId(), author.getId(),
                AdminPiiAccessLog.FIELD_LOGIN_NICKNAME, null, now.minusDays(200)));
        purger.purgeOlderThan(now, 30);
        assertThat(accessLogRepository.existsById(withinYear.getId())).isTrue();
        assertThat(accessLogRepository.existsById(recent.getId())).isFalse(); // 400일 > 365일
    }

    @Test
    void 탈퇴해도_열람_기록은_남는다() throws Exception {
        // 카카오·Apple 은 탈퇴 때 외부 연결 끊기를 부르므로 외부 호출이 없는 GOOGLE 회원으로 확인한다
        User leaving = newUser("탈퇴예정" + UUID.randomUUID().toString().substring(0, 4), SocialType.GOOGLE);
        mockMvc.perform(get("/admin/users/" + leaving.getId() + "/login-name").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(leaving)))
                .andExpect(status().isNoContent());
        assertThat(userRepository.existsById(leaving.getId())).isFalse();
        assertThat(logsOf(leaving)).hasSize(1); // FK 가 없어 id 만 남은 기록이 그대로 있다
    }
}
