package com.hongmap.hongmapbackend.report;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorKeysTest {

    private static final String JWT = "test-secret-test-secret-test-secret-test-secret-0123456789";

    @AfterEach
    void restore() {
        new AuthorKeys("", JWT); // 다른 테스트(같은 JVM)가 쓰는 기본 설정으로 되돌린다
    }

    @Test
    void 같은_사용자는_항상_같은_값이고_사용자마다_다르다() {
        new AuthorKeys("author-secret", JWT);
        String a1 = AuthorKeys.of(1L);
        assertThat(a1).isEqualTo(AuthorKeys.of(1L));
        assertThat(a1).isNotEqualTo(AuthorKeys.of(2L));
        assertThat(a1).hasSize(16).matches("[A-Za-z0-9_-]{16}");
        assertThat(a1).doesNotContain("1");
    }

    @Test
    void 비밀키가_다르면_값도_다르다_AUTHOR_KEY_SECRET_없으면_JWT에서_파생() {
        new AuthorKeys("author-secret", JWT);
        String withAuthorSecret = AuthorKeys.of(7L);
        new AuthorKeys("", JWT);
        String derived = AuthorKeys.of(7L);
        assertThat(derived).isNotNull().isNotEqualTo(withAuthorSecret);
    }

    @Test
    void 키가_하나도_없으면_null() {
        new AuthorKeys("", "");
        assertThat(AuthorKeys.of(1L)).isNull();
        new AuthorKeys("x", JWT);
        assertThat(AuthorKeys.of(null)).isNull();
    }
}
