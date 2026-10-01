package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.auth.token.RefreshToken;
import com.hongmap.hongmapbackend.auth.token.RefreshTokenRepository;
import com.hongmap.hongmapbackend.bookmark.Bookmark;
import com.hongmap.hongmapbackend.bookmark.BookmarkRepository;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.department.Department;
import com.hongmap.hongmapbackend.department.DepartmentRepository;
import com.hongmap.hongmapbackend.department.UserDepartment;
import com.hongmap.hongmapbackend.department.UserDepartmentRepository;
import com.hongmap.hongmapbackend.feedback.Feedback;
import com.hongmap.hongmapbackend.feedback.FeedbackRepository;
import com.hongmap.hongmapbackend.news.News;
import com.hongmap.hongmapbackend.news.NewsRepository;
import com.hongmap.hongmapbackend.notification.KeywordSubscription;
import com.hongmap.hongmapbackend.notification.KeywordSubscriptionRepository;
import com.hongmap.hongmapbackend.notification.NotificationCategory;
import com.hongmap.hongmapbackend.notification.NotificationCategoryRepository;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportFlag;
import com.hongmap.hongmapbackend.report.ReportFlagRepository;
import com.hongmap.hongmapbackend.report.ReportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원탈퇴(DELETE /auth/me)가 users를 참조하는 모든 테이블에 데이터가 있어도 FK 위반 없이 성공하는지 확인한다.
 * H2 스키마는 엔티티 기준(create-drop)이라 FK에 ON DELETE가 없다 — 즉 UserService가 직접 정리하지 않으면 실패한다.
 * users를 참조하는 엔티티를 새로 추가하면 여기에도 데이터를 넣을 것.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserWithdrawIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired NewsRepository newsRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired KeywordSubscriptionRepository keywordSubscriptionRepository;
    @Autowired NotificationCategoryRepository notificationCategoryRepository;
    @Autowired UserDepartmentRepository userDepartmentRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired ReportFlagRepository reportFlagRepository;
    @Autowired FeedbackRepository feedbackRepository;

    @Test
    void 연관_데이터가_모두_있어도_탈퇴에_성공하고_본인_데이터만_정리된다() throws Exception {
        User me = newUser("탈퇴자");
        User other = newUser("다른학생");

        Building building = buildingRepository.save(Building.builder()
                .name("테스트관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        Department department = departmentRepository.save(Department.builder()
                .name("테스트학과-" + UUID.randomUUID()).college("테스트대학").build());
        News news = newsRepository.save(News.builder()
                .title("테스트 공지").category("공지")
                .sourceUrl("https://example.com/" + UUID.randomUUID())
                .publishedAt(LocalDateTime.now())
                .build());

        bookmarkRepository.save(Bookmark.builder().user(me).news(news).build());
        keywordSubscriptionRepository.save(KeywordSubscription.builder().user(me).keyword("장학").build());
        notificationCategoryRepository.save(NotificationCategory.builder().user(me).category("장학").enabled(false).build());
        notificationCategoryRepository.save(NotificationCategory.builder().user(me).category("행사").enabled(true).build());
        userDepartmentRepository.save(UserDepartment.builder().user(me).department(department).isPrimary(true).build());
        userDeviceRepository.save(UserDevice.builder()
                .user(me).pushToken("ExponentPushToken[" + UUID.randomUUID() + "]")
                .tokenType(TokenType.EXPO).platform(DevicePlatform.IOS).build());
        refreshTokenRepository.save(new RefreshToken(me, "a".repeat(64), LocalDateTime.now().plusDays(14)));
        feedbackRepository.save(new Feedback(me, "탈퇴 전에 남긴 문의", null));

        // 내 제보에 다른 사람이 단 신고, 다른 사람 제보에 내가 단 신고
        Report myReport = reportRepository.save(report(me, building));
        Report otherReport = reportRepository.save(report(other, building));
        reportFlagRepository.save(ReportFlag.builder().report(myReport).user(other).reason("SPAM").build());
        reportFlagRepository.save(ReportFlag.builder().report(otherReport).user(me).reason("SPAM").build());

        // 다른 유저의 같은 종류 데이터 — 탈퇴로 지워지면 안 된다
        notificationCategoryRepository.save(NotificationCategory.builder().user(other).category("장학").enabled(false).build());

        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(me)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(me.getId())).isEmpty();
        for (String table : new String[]{
                "bookmarks", "keyword_subscriptions", "notification_categories", "user_departments",
                "user_devices", "refresh_tokens", "reports", "report_flags"}) {
            assertThat(countByUser(table, me.getId())).as(table).isZero();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_flags WHERE report_id = ?", Long.class, myReport.getId())).isZero();

        // 문의는 남고 작성자만 비워진다
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedback WHERE content = '탈퇴 전에 남긴 문의' AND user_id IS NULL", Long.class))
                .isEqualTo(1L);

        // 다른 유저 데이터는 그대로
        assertThat(userRepository.findById(other.getId())).isPresent();
        assertThat(reportRepository.findById(otherReport.getId())).isPresent();
        assertThat(countByUser("notification_categories", other.getId())).isEqualTo(1L);
    }

    @Test
    void 연관_데이터가_없는_유저도_탈퇴에_성공한다() throws Exception {
        User me = newUser("빈계정");

        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(me)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(me.getId())).isEmpty();
    }

    private User newUser(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private Report report(User user, Building building) {
        LocalDateTime now = LocalDateTime.now();
        return Report.builder()
                .user(user).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .build();
    }

    private long countByUser(String table, Long userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, userId);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }
}
