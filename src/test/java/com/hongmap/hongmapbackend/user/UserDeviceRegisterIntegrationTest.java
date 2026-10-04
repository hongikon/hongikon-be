package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.user.dto.DeviceRegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression test for re-registering the same push token. register() deletes the existing row and
 * inserts a new one in one transaction; without flushing the DELETE first, the INSERT runs earlier
 * and violates uq_device_token.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserDeviceRegisterIntegrationTest {

    @Autowired UserDeviceService userDeviceService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void 같은_유저가_같은_토큰을_다시_등록해도_예외_없이_한_행만_남는다() {
        User me = newUser("재등록");
        String token = newToken();

        userDeviceService.register(me.getId(), request(token));
        assertThatCode(() -> userDeviceService.register(me.getId(), request(token)))
                .doesNotThrowAnyException();

        assertThat(countByToken(token)).isEqualTo(1L);
        assertThat(ownerOf(token)).isEqualTo(me.getId());
    }

    @Test
    void 다른_유저가_같은_토큰을_등록하면_소유권이_넘어가고_한_행만_남는다() {
        User a = newUser("기존주인");
        User b = newUser("새주인");
        String token = newToken();

        userDeviceService.register(a.getId(), request(token));
        assertThatCode(() -> userDeviceService.register(b.getId(), request(token)))
                .doesNotThrowAnyException();

        assertThat(countByToken(token)).isEqualTo(1L);
        assertThat(ownerOf(token)).isEqualTo(b.getId());
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
