package com.hongmap.hongmapbackend.user;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberCodesTest {

    @Test
    void 형식은_영문_대문자와_숫자_10자리() {
        assertThat(MemberCodes.isValid("K7Q2M9XA4D")).isTrue();
        assertThat(MemberCodes.isValid("0000000005")).isTrue();
        assertThat(MemberCodes.isValid("k7q2m9xa4d")).isFalse();
        assertThat(MemberCodes.isValid("K7Q2M9XA4")).isFalse();
        assertThat(MemberCodes.isValid("K7Q2M-XA4D")).isFalse();
        assertThat(MemberCodes.isValid(null)).isFalse();
    }

    @Test
    void 검색어는_대소문자_무시하고_앞뒤_공백을_지운다() {
        assertThat(MemberCodes.parse(" k7q2m9xa4d ")).contains("K7Q2M9XA4D");
        assertThat(MemberCodes.parse("K7Q2M9XA4D")).contains("K7Q2M9XA4D");
        assertThat(MemberCodes.parse("K7Q2M9XA4")).isEmpty();
        assertThat(MemberCodes.parse("HIU-482913")).isEmpty();
        assertThat(MemberCodes.parse(null)).isEmpty();
    }

    @Test
    void 무작위_번호는_형식에_맞고_36글자를_고르게_쓴다() {
        MemberCodeGenerator generator = new FixedGenerator(new ArrayDeque<>());
        Set<String> seen = new HashSet<>();
        Map<Character, Integer> counts = new HashMap<>();
        for (int i = 0; i < 3600; i++) {
            String code = generator.randomCode();
            assertThat(MemberCodes.isValid(code)).as(code).isTrue();
            seen.add(code);
            for (char c : code.toCharArray()) {
                counts.merge(c, 1, Integer::sum);
            }
        }
        assertThat(seen).hasSize(3600);
        assertThat(counts).hasSize(36); // 36000글자 중 글자마다 기대값 1000
        assertThat(counts.values()).allSatisfy(n -> assertThat(n).isBetween(800, 1200));
    }

    @Test
    void 이미_쓰인_번호면_다시_뽑는다() {
        FixedGenerator generator = new FixedGenerator(new ArrayDeque<>(List.of(true, true, false)));
        assertThat(MemberCodes.isValid(generator.nextAvailable())).isTrue();
        assertThat(generator.checks.get()).isEqualTo(3);
    }

    @Test
    void 정해진_횟수_모두_겹치면_실패한다() {
        FixedGenerator generator = new FixedGenerator(null); // 늘 사용 중
        assertThatThrownBy(generator::nextAvailable).isInstanceOf(IllegalStateException.class);
        assertThat(generator.checks.get()).isEqualTo(MemberCodeGenerator.MAX_ATTEMPTS);
    }

    /** DB 대신 정해 둔 순서대로 "사용 중" 여부를 돌려준다. null 이면 늘 사용 중. */
    private static final class FixedGenerator extends MemberCodeGenerator {
        private final Deque<Boolean> taken;
        final AtomicInteger checks = new AtomicInteger();

        FixedGenerator(Deque<Boolean> taken) {
            super(null);
            this.taken = taken;
        }

        @Override
        boolean exists(String memberCode) {
            checks.incrementAndGet();
            return taken == null || (!taken.isEmpty() && taken.poll());
        }
    }
}
