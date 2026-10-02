package com.hongmap.hongmapbackend.user;

/**
 * 공개 화면에 보일 작성자 이름 계산. 카카오 닉네임은 실명인 경우가 많아 그대로 내보내지 않는다.
 * 글자 수는 코드포인트 기준이라 이모지(서로게이트 쌍)도 한 글자로 센다.
 */
public final class DisplayNames {

    public static final String ANONYMOUS = "익명";

    private DisplayNames() {
    }

    public static String of(String appNickname, String providerNickname) {
        if (appNickname != null && !appNickname.isBlank()) {
            return appNickname;
        }
        return mask(providerNickname);
    }

    /** "홍길동" → "홍**", "ab" → "a*", "홍" → "홍*", 빈 값 → "익명". */
    public static String mask(String name) {
        if (name == null || name.isBlank()) {
            return ANONYMOUS;
        }
        String trimmed = name.strip();
        int first = trimmed.codePointAt(0);
        int rest = Math.max(1, trimmed.codePointCount(0, trimmed.length()) - 1);
        return new StringBuilder().appendCodePoint(first).append("*".repeat(rest)).toString();
    }
}
