package com.hongmap.hongmapbackend.auth.apple;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppleTokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    @Test
    void 암호화하면_v1_형식이고_매번_달라지며_되돌릴_수_있다() {
        AppleTokenCipher cipher = new AppleTokenCipher(KEY);
        String a = cipher.encrypt("r.apple-refresh-token");
        String b = cipher.encrypt("r.apple-refresh-token");

        assertThat(a).startsWith("v1:").doesNotContain("apple-refresh").isNotEqualTo(b); // IV 무작위
        assertThat(cipher.decrypt(a)).isEqualTo("r.apple-refresh-token");
        assertThat(cipher.decrypt(b)).isEqualTo("r.apple-refresh-token");
    }

    @Test
    void 변조되거나_다른_키면_복호화하지_않는다() {
        AppleTokenCipher cipher = new AppleTokenCipher(KEY);
        String encrypted = cipher.encrypt("r.token");
        char[] chars = encrypted.toCharArray();
        chars[10] = chars[10] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> cipher.decrypt(new String(chars))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AppleTokenCipher(OTHER_KEY).decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키가_없으면_꺼지고_평문_저장을_거부한다() {
        AppleTokenCipher disabled = new AppleTokenCipher("");
        assertThat(disabled.isEnabled()).isFalse();
        assertThatThrownBy(() -> disabled.encrypt("r.token")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AppleRefreshTokenConverter(disabled).convertToDatabaseColumn("r.token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키_형식이_틀리면_기동하지_않는다() {
        assertThatThrownBy(() -> new AppleTokenCipher("not base64!!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AppleTokenCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32바이트");
    }

    @Test
    void 컨버터는_암호문을_풀고_읽을_수_없으면_null_암호화_전_평문은_그대로_읽는다() {
        AppleRefreshTokenConverter converter = new AppleRefreshTokenConverter(new AppleTokenCipher(KEY));
        String column = converter.convertToDatabaseColumn("r.token");

        assertThat(converter.convertToEntityAttribute(column)).isEqualTo("r.token");
        assertThat(new AppleRefreshTokenConverter(new AppleTokenCipher(OTHER_KEY)).convertToEntityAttribute(column)).isNull();
        assertThat(converter.convertToEntityAttribute("r.legacy-plain")).isEqualTo("r.legacy-plain");
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void prod에서_Apple_로그인이_켜져_있는데_키가_없으면_기동하지_않는다() {
        AppleProperties props = mock(AppleProperties.class);
        when(props.getClientIds()).thenReturn(List.of("com.hongmap.alimi"));
        AppleAuthClient configured = mock(AppleAuthClient.class);
        when(configured.isConfigured()).thenReturn(true);
        AppleAuthClient notConfigured = mock(AppleAuthClient.class);

        // 키 모두 있음 → 통과
        new AppleStartupCheck(props, configured, new AppleTokenCipher(KEY), true).afterPropertiesSet();
        // Apple 키 없음 / 암호화 키 없음 → 실패
        assertThatThrownBy(() -> new AppleStartupCheck(props, notConfigured, new AppleTokenCipher(KEY), true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPLE_PRIVATE_KEY");
        assertThatThrownBy(() -> new AppleStartupCheck(props, configured, new AppleTokenCipher(""), true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPLE_TOKEN_ENC_KEY");
        // prod 가 아니면(require=false) 키가 없어도 기동
        new AppleStartupCheck(props, notConfigured, new AppleTokenCipher(""), false).afterPropertiesSet();
        // Apple 로그인을 끈 경우(APPLE_CLIENT_IDS 빈 값) 키가 없어도 기동
        AppleProperties off = mock(AppleProperties.class);
        when(off.getClientIds()).thenReturn(List.of());
        new AppleStartupCheck(off, notConfigured, new AppleTokenCipher(""), true).afterPropertiesSet();
    }
}
