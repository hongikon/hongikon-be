package com.hongmap.hongmapbackend.user.dto;

import com.hongmap.hongmapbackend.user.DevicePlatform;
import com.hongmap.hongmapbackend.user.TokenType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.regex.Pattern;

/**
 * 푸시 기기 등록. push_token 컬럼이 varchar(255) 라 더 길면 DB 오류(500)가 났고, 아무 문자열이나 받아 두면
 * Expo 발송 때마다 "not a valid Expo push token" 오류만 쌓였다 → 길이와 Expo 토큰 형식을 여기서 400 으로 거른다.
 */
public record DeviceRegisterRequest(
        @NotBlank(message = "푸시 토큰이 비어 있어요.")
        @Size(max = 255, message = "푸시 토큰이 너무 길어요.")
        String pushToken,

        @NotNull
        TokenType tokenType,

        DevicePlatform platform
) {
    /**
     * Expo 토큰 형식. expo-server-sdk 의 Expo.isExpoPushToken 과 같은 기준(ExponentPushToken[…] 또는 ExpoPushToken[…]).
     * 괄호 안 값은 공백·대괄호 없이 1자 이상.
     */
    private static final Pattern EXPO_TOKEN = Pattern.compile("^Expo(nent)?PushToken\\[[^\\[\\]\\s]+]$");

    /** EXPO 토큰만 형식을 본다(FCM·APNS 는 지금 앱이 보내지 않고 형식도 달라 길이만 본다). */
    @AssertTrue(message = "올바른 푸시 토큰 형식이 아니에요.")
    public boolean isPushTokenFormatValid() {
        if (tokenType != TokenType.EXPO || pushToken == null || pushToken.isBlank()) {
            return true; // 빈 값·종류 누락은 @NotBlank·@NotNull 이 따로 알린다.
        }
        return EXPO_TOKEN.matcher(pushToken).matches();
    }
}
