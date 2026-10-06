package com.hongmap.hongmapbackend.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AppNicknamePolicyTest {

    @Test
    void normalizeTrimsAndTreatsBlankAsClear() {
        assertThat(AppNicknamePolicy.normalize("  홍익러버 ")).isEqualTo("홍익러버");
        assertThat(AppNicknamePolicy.normalize("   ")).isNull();
        assertThat(AppNicknamePolicy.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"홍익", "abc_123", "Mapo_Cat", "가나다라마바사아자차카타"})
    void acceptsValidNicknames(String nickname) {
        assertThat(AppNicknamePolicy.violation(nickname)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "가나다라마바사아자차카타파"})
    void rejectsWrongLength(String nickname) {
        assertThat(AppNicknamePolicy.violation(nickname)).hasValueSatisfying(m -> assertThat(m).contains("2~12자"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"홍 길동", "ㅎㅇ", "hi!", "😀😀", "홍**"})
    void rejectsDisallowedCharacters(String nickname) {
        assertThat(AppNicknamePolicy.violation(nickname)).hasValueSatisfying(m -> assertThat(m).contains("한글, 영문"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"운영진", "홍익온공식", "ADMIN_kim", "HongikOn", "학교대표", "관_리자", "운영1", "경영학생회", "학생_회장"})
    void rejectsReservedWords(String nickname) {
        assertThat(AppNicknamePolicy.violation(nickname)).hasValueSatisfying(m -> assertThat(m).contains("쓸 수 없어요"));
    }

    @Test
    void limiterAllowsFivePerDayPerUser() {
        MutableClock clock = new MutableClock();
        AppNicknameChangeLimiter limiter = new AppNicknameChangeLimiter(clock);
        for (int i = 0; i < AppNicknameChangeLimiter.MAX_CHANGES; i++) {
            assertThat(limiter.tryAcquire(1L)).isTrue();
        }
        assertThat(limiter.tryAcquire(1L)).isFalse();
        assertThat(limiter.tryAcquire(2L)).isTrue();

        clock.now = clock.now.plus(Duration.ofHours(24));
        assertThat(limiter.tryAcquire(1L)).isTrue();
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-02T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
