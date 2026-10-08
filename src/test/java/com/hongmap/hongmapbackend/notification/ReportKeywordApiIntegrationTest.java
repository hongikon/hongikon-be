package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.building.BuildingRepository;
import com.hongmap.hongmapbackend.push.ExpoPushClient;
import com.hongmap.hongmapbackend.push.ExpoPushMessage;
import com.hongmap.hongmapbackend.push.ExpoPushTicket;
import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.report.ReportCategory;
import com.hongmap.hongmapbackend.report.ReportRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제보 전용 키워드 API(/users/me/report-keywords), 새 제보 알림 범위 KEYWORDS, 관리자 승인 → 키워드 푸시 연결.
 * H2 인메모리 DB, Expo API는 ExpoPushClient를 모킹한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportKeywordApiIntegrationTest {

    private static final String BASE = "/users/me/report-keywords";
    private static final String SETTINGS = "/users/me/notification-settings";

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired ReportKeywordSubscriptionRepository reportKeywordRepository;
    @Autowired KeywordSubscriptionRepository newsKeywordRepository;
    @Autowired BuildingRepository buildingRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ExpoPushClient expoPushClient;

    User user;
    List<ExpoPushMessage> sent;

    @BeforeEach
    void setUp() {
        user = user("학생");
        sent = Collections.synchronizedList(new ArrayList<>());
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            sent.addAll(batch);
            return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
        });
    }

    // ---------- CRUD ----------

    @Test
    void 추가하면_201과_id_keyword를_돌려주고_목록에_등록_순서대로_보인다() throws Exception {
        long first = create(user, "  간식 ");
        long second = create(user, "붕어빵");

        mockMvc.perform(get(BASE).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords", hasSize(2)))
                .andExpect(jsonPath("$.keywords[0].id").value(first))
                .andExpect(jsonPath("$.keywords[0].keyword").value("간식"))
                .andExpect(jsonPath("$.keywords[1].id").value(second))
                .andExpect(jsonPath("$.keywords[1].keyword").value("붕어빵"));
    }

    @Test
    void 소식_키워드와_따로_저장된다() throws Exception {
        create(user, "장학");

        assertThat(newsKeywordRepository.findByUser_Id(user.getId())).isEmpty();
        mockMvc.perform(get("/users/me/keyword-subscriptions").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.keywords", hasSize(0)));
    }

    @Test
    void 같은_키워드는_대소문자를_무시하고_409() throws Exception {
        create(user, "Coffee");
        mockMvc.perform(post(BASE).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"keyword\":\" coffee \"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void 빈_키워드와_30자를_넘는_키워드는_400() throws Exception {
        mockMvc.perform(post(BASE).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"keyword\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"keyword\":\"" + "가".repeat(31) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 유저당_30개를_넘으면_400() throws Exception {
        for (int i = 0; i < ReportKeywordSubscriptionService.MAX_KEYWORDS_PER_USER; i++) {
            create(user, "키워드" + i);
        }
        mockMvc.perform(post(BASE).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"keyword\":\"하나더\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 삭제는_204이고_다른_사람_키워드는_404이며_목록에도_보이지_않는다() throws Exception {
        User other = user("다른학생");
        long othersId = create(other, "비밀키워드");
        long mine = create(user, "간식");

        mockMvc.perform(get(BASE).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.keywords", hasSize(1)))
                .andExpect(jsonPath("$.keywords[0].keyword").value("간식"));

        mockMvc.perform(delete(BASE + "/" + othersId).header("Authorization", bearer(user)))
                .andExpect(status().isNotFound());
        assertThat(reportKeywordRepository.findById(othersId)).isPresent();

        mockMvc.perform(delete(BASE + "/" + mine).header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());
        assertThat(reportKeywordRepository.findById(mine)).isEmpty();

        mockMvc.perform(delete(BASE + "/" + mine).header("Authorization", bearer(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"keyword\":\"간식\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE + "/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void 회원탈퇴하면_제보_키워드도_지워진다() throws Exception {
        create(user, "간식");
        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());
        assertThat(reportKeywordRepository.findByUser_IdOrderByIdAsc(user.getId())).isEmpty();
    }

    // ---------- 범위 ----------

    @Test
    void 범위는_KEYWORDS와_CAMPUS를_받는다() throws Exception {
        mockMvc.perform(patch(SETTINGS).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newReports\":true,\"newReportsScope\":\"KEYWORDS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newReportsScope").value("KEYWORDS"));
        mockMvc.perform(get(SETTINGS).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.newReports").value(true))
                .andExpect(jsonPath("$.newReportsScope").value("KEYWORDS"));

        mockMvc.perform(patch(SETTINGS).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newReportsScope\":\"CAMPUS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newReportsScope").value("CAMPUS"));

        mockMvc.perform(patch(SETTINGS).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newReportsScope\":\"keywords\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newReportsScope").value("KEYWORDS"));
    }

    // ---------- 관리자 승인 → 키워드 푸시 ----------

    @Test
    void 관리자가_승인하면_본문에_키워드가_있는_제보도_키워드_알림으로_간다() throws Exception {
        User admin = user("관리자");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        User author = user("작성자");
        String token = device(user);
        create(user, "간식행사");
        mockMvc.perform(patch(SETTINGS).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newReports\":true,\"newReportsScope\":\"KEYWORDS\"}"))
                .andExpect(status().isOk());
        Report report = pendingReport(author, "오늘 학생회 간식 행사 있어요");

        mockMvc.perform(patch("/admin/reports/" + report.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        verify(expoPushClient, timeout(5000).atLeastOnce()).send(anyList());
        assertThat(messagesTo(token)).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("[간식행사] 새 제보 · " + report.getBuilding().getName() + " 1층");
            assertThat(m.body()).isEqualTo("무료 나눔");
            assertThat(m.data()).containsEntry("type", "REPORT_NEW").containsEntry("reportId", report.getId());
        });
    }

    // ---------- 픽스처 ----------

    private long create(User owner, String keyword) throws Exception {
        String body = "{\"keyword\":\"" + keyword + "\"}";
        String response = mockMvc.perform(post(BASE).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keyword").value(keyword.trim()))
                .andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(response);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private List<ExpoPushMessage> messagesTo(String token) {
        synchronized (sent) {
            return sent.stream().filter(m -> m.to().equals(token)).toList();
        }
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private User user(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private String device(User owner) {
        String token = "ExponentPushToken[" + UUID.randomUUID() + "]";
        userDeviceRepository.save(UserDevice.builder().user(owner).pushToken(token).tokenType(TokenType.EXPO).build());
        return token;
    }

    private Report pendingReport(User author, String content) {
        Building building = buildingRepository.save(Building.builder()
                .name("테스트관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        LocalDateTime now = LocalDateTime.now();
        return reportRepository.save(Report.builder()
                .user(author).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("무료 나눔").content(content)
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .build());
    }
}
