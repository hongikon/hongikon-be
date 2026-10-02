package com.hongmap.hongmapbackend.user;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberCodesTest {

    @Test
    void 형식은_접두사_하이픈_6자리() {
        assertThat(MemberCodes.format("HIU", 482913)).isEqualTo("HIU-482913");
        assertThatThrownBy(() -> MemberCodes.format("HIU", 99_999)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberCodes.format("hiu", 482913)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberCodes.format("H", 482913)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 검색어는_대소문자_무시하고_접두사_있거나_없거나() {
        assertThat(MemberCodes.parseFull(" hiu-482913 ")).contains("HIU-482913");
        assertThat(MemberCodes.parseFull("HIU-482913")).contains("HIU-482913");
        assertThat(MemberCodes.parseFull("HIU-48291")).isEmpty();
        assertThat(MemberCodes.parseFull("482913")).isEmpty();
        assertThat(MemberCodes.isNumberPart("482913")).isTrue();
        assertThat(MemberCodes.isNumberPart("82913")).isFalse();
        assertThat(MemberCodes.isNumberPart("082913")).isFalse();
    }

    @Test
    void 무작위_번호는_범위_안이고_고르게_흩어진다() {
        MemberCodeGenerator generator = new FixedGenerator(new ArrayDeque<>());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String code = generator.randomCode();
            assertThat(code).matches("HIU-[1-9]\\d{5}");
            seen.add(code);
        }
        assertThat(seen.size()).isGreaterThan(1990); // 90만 칸에 2000개 — 거의 겹치지 않는다
    }

    @Test
    void 이미_쓰인_번호면_다시_뽑는다() {
        Deque<Boolean> taken = new ArrayDeque<>(java.util.List.of(true, true, false));
        FixedGenerator generator = new FixedGenerator(taken);
        assertThat(generator.nextAvailable()).matches("HIU-[1-9]\\d{5}");
        assertThat(generator.checks.get()).isEqualTo(3);
    }

    @Test
    void 정해진_횟수_모두_겹치면_실패한다() {
        FixedGenerator generator = new FixedGenerator(null); // 늘 사용 중
        assertThatThrownBy(generator::nextAvailable).isInstanceOf(IllegalStateException.class);
        assertThat(generator.checks.get()).isEqualTo(MemberCodeGenerator.MAX_ATTEMPTS);
    }

    @Test
    void 접두사_설정이_잘못되면_시작하지_않는다() {
        assertThatThrownBy(() -> new MemberCodeGenerator("hongik-univ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new MemberCodeGenerator(" ABC ", null).prefix()).isEqualTo("ABC");
    }

    /** DB 대신 정해 둔 순서대로 "사용 중" 여부를 돌려준다. null 이면 늘 사용 중. */
    private static final class FixedGenerator extends MemberCodeGenerator {
        private final Deque<Boolean> taken;
        final AtomicInteger checks = new AtomicInteger();

        FixedGenerator(Deque<Boolean> taken) {
            super("HIU", null);
            this.taken = taken;
        }

        @Override
        boolean exists(String memberCode) {
            checks.incrementAndGet();
            return taken == null || (!taken.isEmpty() && taken.poll());
        }
    }
}
