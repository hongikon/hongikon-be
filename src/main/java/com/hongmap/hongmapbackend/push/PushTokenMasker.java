package com.hongmap.hongmapbackend.push;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그용 푸시 토큰 가리기. 푸시 토큰은 그 기기로 알림을 보낼 수 있는 값이라 로그에 원문을 남기지 않는다(보안 점검 로깅 항목).
 * 같은 기기를 구분할 수 있도록 끝 4자만 남긴다: {@code ExponentPushToken[abcdefgh1234]} → {@code ExponentPushToken[…1234]}.
 */
public final class PushTokenMasker {

    /** Expo 응답 메시지 안에 섞여 오는 토큰도 찾아 가린다. */
    private static final Pattern EXPO_TOKEN = Pattern.compile("(Expo(?:nent)?PushToken)\\[([^\\]]*)\\]");

    private PushTokenMasker() {
    }

    public static String mask(String token) {
        if (token == null) {
            return null;
        }
        Matcher m = EXPO_TOKEN.matcher(token);
        if (m.matches()) {
            return m.group(1) + "[" + tail(m.group(2)) + "]";
        }
        return tail(token);
    }

    /** 문자열 안의 Expo 토큰을 모두 가린다(Expo 오류 메시지용). */
    public static String maskWithin(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = EXPO_TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + "[" + tail(m.group(2)) + "]"));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String tail(String value) {
        return value.length() <= 4 ? "…" : "…" + value.substring(value.length() - 4);
    }
}
