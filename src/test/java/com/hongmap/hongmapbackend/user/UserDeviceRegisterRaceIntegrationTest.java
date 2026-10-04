package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.auth.jwt.JwtTokenProvider;
import com.hongmap.hongmapbackend.user.dto.DeviceRegisterRequest;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 같은 푸시 토큰이 거의 동시에 두 번 등록될 때(확인 → INSERT 사이 경합) uq_device_token 위반으로 500 이 나던 문제의 회귀 테스트,
 * 그리고 토큰 길이·형식 검증(400).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserDeviceRegisterRaceIntegrationTest {

    @Autowired UserDeviceService userDeviceService;
    @Autowired UserRepository userRepository;
    @MockitoSpyBean UserDeviceRepository userDeviceRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Test
    void 확인과_INSERT_사이에_다른_요청이_먼저_넣어도_예외_없이_한_행만_남는다() {
        User a = newUser("먼저");
        User b = newUser("나중");
        String token = newToken();
        userDeviceService.register(a.getId(), request(token));

        // 경합 재현: b 의 첫 시도는 "아직 없음"을 본다(그 사이 a 의 행이 커밋된 상황) → INSERT 가 유니크 위반.
        // 스파이는 리포지토리 프록시에 위임하는 기본 Answer 를 갖고 있어(callRealMethod 불가) 두 번째부터는 그걸 쓴다.
        Answer<?> real = mockingDetails(userDeviceRepository).getMockCreationSettings().getDefaultAnswer();
        doReturn(Optional.empty()).doAnswer(real).when(userDeviceRepository).findByPushToken(token);

        userDeviceService.register(b.getId(), request(token));

        assertThat(countByToken(token)).isEqualTo(1L);
        assertThat(ownerOf(token)).isEqualTo(b.getId());
    }

    @Test
    void 두_스레드가_같은_토큰을_동시에_등록해도_둘_다_성공하고_한_행만_남는다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                User a = newUser("동시A");
                User b = newUser("동시B");
                String token = newToken();
                CyclicBarrier barrier = new CyclicBarrier(2);
                List<Future<?>> futures = new ArrayList<>();
                for (User user : List.of(a, b)) {
                    futures.add(pool.submit(() -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        return userDeviceService.register(user.getId(), request(token));
                    }));
                }
                for (Future<?> future : futures) {
                    future.get(20, TimeUnit.SECONDS); // 예외면 여기서 ExecutionException
                }
                assertThat(countByToken(token)).isEqualTo(1L);
                assertThat(ownerOf(token)).isIn(a.getId(), b.getId());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void Expo_토큰_형식이_아니거나_너무_길면_400() throws Exception {
        User me = newUser("형식");
        for (String bad : List.of("not-a-token", "ExponentPushToken[]", "ExponentPushToken[a b]",
                "ExponentPushToken[" + "x".repeat(300) + "]")) {
            mockMvc.perform(post("/users/me/devices").header("Authorization", bearer(me))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pushToken\":\"" + bad + "\",\"tokenType\":\"EXPO\",\"platform\":\"IOS\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        // 구형 접두사(ExpoPushToken)와 현행(ExponentPushToken) 모두 받는다.
        for (String good : List.of("ExpoPushToken[" + UUID.randomUUID() + "]", newToken())) {
            mockMvc.perform(post("/users/me/devices").header("Authorization", bearer(me))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pushToken\":\"" + good + "\",\"tokenType\":\"EXPO\",\"platform\":\"IOS\"}"))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId());
    }

    private User newUser(String nickname) {
        return userRepository.save(User.builder()
                .socialId(UUID.randomUUID().toString()).socialType(SocialType.KAKAO).nickname(nickname).build());
    }

    private String newToken() {
        return "ExponentPushToken[" + UUID.randomUUID() + "]";
    }

    private DeviceRegisterRequest request(String token) {
        return new DeviceRegisterRequest(token, TokenType.EXPO, DevicePlatform.IOS);
    }

    private long countByToken(String token) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_devices WHERE push_token = ?", Long.class, token);
    }

    private Long ownerOf(String token) {
        return jdbcTemplate.queryForObject("SELECT user_id FROM user_devices WHERE push_token = ?", Long.class, token);
    }
}
