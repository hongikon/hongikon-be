package com.hongmap.hongmapbackend.comment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 authorKey 가 PR #13 report.AuthorKeys 와 같은 값을 내는지 고정한다(앱의 "이 사용자 숨기기"가 제보·댓글에 함께 맞도록).
 * 기대값은 #13 의 AuthorKeys 로 같은 입력을 계산한 결과다.
 */
class CommentAuthorKeysTest {

    @Test
    void 같은_사용자는_같은_16자_값이고_사용자마다_다르다() {
        CommentAuthorKeys keys = new CommentAuthorKeys("fixed-author-secret", "ignored-jwt");
        String a = keys.of(42L);
        assertThat(a).hasSize(16).matches("[A-Za-z0-9_-]{16}").isEqualTo(keys.of(42L));
        assertThat(keys.of(43L)).isNotEqualTo(a);
        assertThat(a).doesNotContain("42");
    }

    @Test
    void 키가_없으면_null_JWT_비밀키에서_파생도_된다() {
        assertThat(new CommentAuthorKeys("", "").of(1L)).isNull();
        assertThat(new CommentAuthorKeys("s", "j").of(null)).isNull();
        assertThat(new CommentAuthorKeys("", "jwt-secret").of(1L)).hasSize(16);
    }

    @Test
    void PR13_AuthorKeys_와_같은_값() {
        assertThat(new CommentAuthorKeys("fixed-author-secret", "").of(42L)).isEqualTo(EXPECTED_42_FIXED);
        assertThat(new CommentAuthorKeys("", "jwt-secret").of(7L)).isEqualTo(EXPECTED_7_JWT);
    }

    // #13 브랜치(feat/ugc-moderation)의 AuthorKeys 로 계산한 값.
    static final String EXPECTED_42_FIXED = "eGT9QFpzEFFU4VqL";
    static final String EXPECTED_7_JWT = "NTlN5u6dWzga1Yx3";
}
