package com.hongmap.hongmapbackend.push;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.notification.UserNotificationSetting;
import com.hongmap.hongmapbackend.notification.UserNotificationSettingRepository;
import com.hongmap.hongmapbackend.user.SocialType;
import com.hongmap.hongmapbackend.user.SuspendedUserInterceptor;
import com.hongmap.hongmapbackend.user.TokenType;
import com.hongmap.hongmapbackend.user.User;
import com.hongmap.hongmapbackend.user.UserDevice;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import com.hongmap.hongmapbackend.user.UserRepository;
import com.hongmap.hongmapbackend.user.UserSuspensionChangedEvent;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이용 정지·해제 알림(법무 검토: 제한 사유 고지·이의 제기 기회). 관리자 정지/해제 → 커밋 후 비동기 푸시(사유·14일 이의 제기 안내),
 * 알림 설정과 무관, 기기 없으면 없음, 발송 실패해도 관리자 작업은 성공. 그리고 GET /users/me 의 상태 필드와 사유가 담긴 403.
 * Expo API 는 ExpoPushClient 를 모킹한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountStatusPushDispatcherTest {

    @Autowired AccountStatusPushDispatcher dispatcher;
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired UserDeviceRepository userDeviceRepository;
    @Autowired UserNotificationSettingRepository settingRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ExpoPushClient expoPushClient;

    String run;
    List<ExpoPushMessage> sent;
    User admin;

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        sent = Collections.synchronizedList(new ArrayList<>());
        when(expoPushClient.send(anyList())).thenAnswer(inv -> {
            List<ExpoPushMessage> batch = inv.getArgument(0);
            sent.addAll(batch);
            return batch.stream().map(m -> new ExpoPushTicket("ok", "ticket-id", null, null)).toList();
        });
        admin = user("관리자");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    @Test
    void 정지하면_알림_설정을_꺼도_사유와_이의_제기_안내를_보내고_해제하면_풀렸다고_보낸다() throws Exception {
        User member = user("학생");
        String token = device(member, true);
        device(member, false); // 비활성 기기는 제외
        UserNotificationSetting setting = new UserNotificationSetting(member.getId());
        setting.changeReportStatusEnabled(false); // 서비스 고지라 알림 설정과 무관
        settingRepository.save(setting);

        mockMvc.perform(post("/admin/users/" + member.getId() + "/suspend").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"욕설 반복\"}"))
                .andExpect(status().isOk());

        ExpoPushMessage suspended = awaitMessage(token, "ACCOUNT_SUSPENDED");
        assertThat(suspended.title()).isEqualTo("이용이 제한됐어요");
        assertThat(suspended.body()).contains("사유: 욕설 반복")
                .contains("이의가 있으면 14일 안에 hongikonsupport@gmail.com 으로 알려 주세요");
        assertThat(suspended.data()).isEqualTo(Map.of("type", "ACCOUNT_SUSPENDED"));
        assertThat(mine()).hasSize(1);

        mockMvc.perform(post("/admin/users/" + member.getId() + "/unsuspend").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        ExpoPushMessage unsuspended = awaitMessage(token, "ACCOUNT_UNSUSPENDED");
        assertThat(unsuspended.title()).isEqualTo("이용 제한이 풀렸어요");
    }

    @Test
    void 기기가_없으면_아무것도_보내지_않고_발송이_실패해도_정지는_된다() throws Exception {
        User noDevice = user("기기없음");
        assertThat(dispatcher.dispatch(new UserSuspensionChangedEvent(noDevice.getId(), true, "도배"))).isZero();

        User member = user("학생2");
        device(member, true);
        when(expoPushClient.send(anyList())).thenThrow(new IllegalStateException("expo down"));

        mockMvc.perform(post("/admin/users/" + member.getId() + "/suspend").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"도배\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(userRepository.findById(member.getId()).orElseThrow().isSuspended()).isTrue();
    }

    @Test
    void 정지된_회원은_내_정보에서_상태와_사유를_보고_쓰기_요청은_사유가_담긴_403을_받는다() throws Exception {
        User member = user("학생3");

        mockMvc.perform(get("/users/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.suspendedReason").value(nullValue()))
                .andExpect(jsonPath("$.suspendedAt").value(nullValue()));

        mockMvc.perform(post("/admin/users/" + member.getId() + "/suspend").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"허위 제보 반복\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspendedReason").value("허위 제보 반복"))
                .andExpect(jsonPath("$.suspendedAt").isString());
        mockMvc.perform(post("/feedback").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"문의\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "운영 정책 위반으로 이용이 제한된 계정이에요(사유: 허위 제보 반복). 제보·신고·문의를 할 수 없어요. "
                                + "이의 제기: hongikonsupport@gmail.com"));
        assertThat(SuspendedUserInterceptor.suspendedMessage(null)).isEqualTo(SuspendedUserInterceptor.SUSPENDED_MESSAGE);
    }

    private ExpoPushMessage awaitMessage(String token, String type) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            synchronized (sent) {
                for (ExpoPushMessage m : sent) {
                    if (m.to().equals(token) && type.equals(m.data().get("type"))) {
                        return m;
                    }
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError(type + " 알림이 오지 않았다");
    }

    private List<ExpoPushMessage> mine() {
        synchronized (sent) {
            return sent.stream().filter(m -> m.to().contains(run)).toList();
        }
    }

    private User user(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private String device(User user, boolean active) {
        String token = "ExponentPushToken[" + run + "-" + UUID.randomUUID() + "]";
        UserDevice device = UserDevice.builder().user(user).pushToken(token).tokenType(TokenType.EXPO).build();
        if (!active) {
            device.deactivate();
        }
        userDeviceRepository.save(device);
        return token;
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }
}
