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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제보 알림 설정 API(/users/me/notification-settings)와, 관리자 제보 검토 → 커밋 후 비동기 푸시 연결.
 * H2 인메모리 DB, Expo API는 ExpoPushClient를 모킹한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationSettingApiIntegrationTest {

    private static final String BASE = "/users/me/notification-settings";

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired UserNotificationSettingRepository settingRepository;
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

    // ---------- 설정 API ----------

    @Test
    void 저장한_적_없으면_기본값을_내려준다() throws Exception {
        mockMvc.perform(get(BASE).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportStatus").value(true))
                .andExpect(jsonPath("$.newReports").value(false))
                .andExpect(jsonPath("$.newReportsScope").value("CAMPUS"));
        assertThat(settingRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void PATCH는_보낸_필드만_바꾼다() throws Exception {
        mockMvc.perform(patch(BASE).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newReports\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportStatus").value(true))
                .andExpect(jsonPath("$.newReports").value(true));

        mockMvc.perform(patch(BASE).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reportStatus\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportStatus").value(false))
                .andExpect(jsonPath("$.newReports").value(true));

        mockMvc.perform(get(BASE).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.reportStatus").value(false))
                .andExpect(jsonPath("$.newReports").value(true));
    }

    @Test
    void 알_수_없는_범위는_400() throws Exception {
        mockMvc.perform(patch(BASE).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newReportsScope\":\"NEARBY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 범위_값이_너무_길면_요청_검증에서_400이고_아무것도_바꾸지_않는다() throws Exception {
        mockMvc.perform(patch(BASE).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newReports\":true,\"newReportsScope\":\"" + "C".repeat(5000) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.newReports").value(false));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"newReports\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 회원탈퇴하면_설정도_지워진다() throws Exception {
        mockMvc.perform(patch(BASE).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newReports\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/auth/me").header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());

        assertThat(settingRepository.findById(user.getId())).isEmpty();
    }

    // ---------- 관리자 검토 → 비동기 푸시 ----------

    @Test
    void 관리자가_승인하면_응답_뒤에_작성자에게_결과_푸시가_간다() throws Exception {
        User admin = admin();
        String token = device(user);
        Report report = pendingReport(user);

        mockMvc.perform(patch("/admin/reports/" + report.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(expoPushClient, timeout(5000).atLeastOnce()).send(anyList());
        assertThat(messagesTo(token)).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("제보가 지도에 올라갔어요");
            assertThat(m.data()).containsEntry("type", "REPORT_STATUS")
                    .containsEntry("reportId", report.getId())
                    .containsEntry("status", "ACTIVE");
        });
    }

    @Test
    void 결과_알림을_끈_작성자는_반려돼도_푸시를_받지_않는다() throws Exception {
        User admin = admin();
        String token = device(user);
        mockMvc.perform(patch(BASE).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reportStatus\":false}"))
                .andExpect(status().isOk());
        Report report = pendingReport(user);

        mockMvc.perform(patch("/admin/reports/" + report.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\",\"note\":\"중복 제보\"}"))
                .andExpect(status().isOk());

        verify(expoPushClient, after(1000).never()).send(anyList());
        assertThat(messagesTo(token)).isEmpty();
    }

    // ---------- 픽스처 ----------

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

    private User admin() {
        User admin = user("관리자");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        return admin;
    }

    private String device(User owner) {
        String token = "ExponentPushToken[" + UUID.randomUUID() + "]";
        userDeviceRepository.save(UserDevice.builder().user(owner).pushToken(token).tokenType(TokenType.EXPO).build());
        return token;
    }

    private Report pendingReport(User author) {
        Building building = buildingRepository.save(Building.builder()
                .name("테스트관-" + UUID.randomUUID())
                .latitude(new BigDecimal("37.5500000")).longitude(new BigDecimal("126.9250000"))
                .build());
        LocalDateTime now = LocalDateTime.now();
        return reportRepository.save(Report.builder()
                .user(author).building(building).floor(1)
                .lat(new BigDecimal("37.5500000")).lng(new BigDecimal("126.9250000"))
                .category(ReportCategory.FOOD_TRUCK).title("붕어빵 트럭")
                .startsAt(now.minusHours(1)).endsAt(now.plusHours(3))
                .build());
    }
}
