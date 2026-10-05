package com.hongmap.hongmapbackend.auth.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 만료된 refresh 세션 row 정리. 유저당 여러 세션을 두면서 로그아웃 없이 버려진 세션(앱 삭제, 브라우저 데이터 삭제,
 * 동시 재발급으로 갈라진 뒤 안 쓰인 사슬)이 쌓이므로 하루 한 번 지운다. 로그인 때도 그 유저 몫은 정리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanup {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "${jwt.refresh-cleanup-cron:0 40 4 * * *}")
    public void deleteExpired() {
        int deleted = refreshTokenRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("만료된 refresh 세션 {}건 삭제", deleted);
        }
    }
}
