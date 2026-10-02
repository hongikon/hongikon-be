package com.hongmap.hongmapbackend.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayNamesTest {

    @Test
    void masksAllButFirstCodePoint() {
        assertThat(DisplayNames.mask("홍길동")).isEqualTo("홍**");
        assertThat(DisplayNames.mask("ab")).isEqualTo("a*");
        assertThat(DisplayNames.mask("  김철수  ")).isEqualTo("김**");
        assertThat(DisplayNames.mask("Apple 사용자 1a2b")).isEqualTo("A*************");
    }

    @Test
    void singleCharacterStillGetsOneStar() {
        assertThat(DisplayNames.mask("홍")).isEqualTo("홍*");
    }

    @Test
    void countsSurrogatePairsAsOneCharacter() {
        assertThat(DisplayNames.mask("😀하하")).isEqualTo("😀**");
        assertThat(DisplayNames.mask("가😀")).isEqualTo("가*");
    }

    @Test
    void blankBecomesAnonymous() {
        assertThat(DisplayNames.mask(null)).isEqualTo("익명");
        assertThat(DisplayNames.mask("   ")).isEqualTo("익명");
    }

    @Test
    void appNicknameWinsOverMaskedName() {
        assertThat(DisplayNames.of("홍익러버", "홍길동")).isEqualTo("홍익러버");
        assertThat(DisplayNames.of(null, "홍길동")).isEqualTo("홍**");
        assertThat(DisplayNames.of(" ", "홍길동")).isEqualTo("홍**");
    }
}
