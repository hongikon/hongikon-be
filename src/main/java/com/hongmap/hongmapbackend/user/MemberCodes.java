package com.hongmap.hongmapbackend.user;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 공개 회원 번호 형식: {@code <학교 접두사>-<6자리 숫자>} (예: HIU-482913).
 * 숫자는 100000~999999 라 앞자리가 0 이 아니고, 접두사는 영문 대문자 2~8자라 전체 길이는 최대 15자(컬럼 16자).
 */
public final class MemberCodes {

    public static final int MIN_NUMBER = 100_000;
    public static final int MAX_NUMBER = 999_999;

    private static final Pattern PREFIX = Pattern.compile("[A-Z]{2,8}");
    private static final Pattern FULL = Pattern.compile("[A-Z]{2,8}-[1-9]\\d{5}");
    private static final Pattern DIGITS = Pattern.compile("[1-9]\\d{5}");

    private MemberCodes() {
    }

    public static String format(String prefix, int number) {
        if (number < MIN_NUMBER || number > MAX_NUMBER) {
            throw new IllegalArgumentException("회원 번호 숫자는 6자리여야 합니다: " + number);
        }
        return requireValidPrefix(prefix) + "-" + number;
    }

    public static String requireValidPrefix(String prefix) {
        if (prefix == null || !PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("회원 번호 접두사는 영문 대문자 2~8자여야 합니다(app.member-code.school-prefix): " + prefix);
        }
        return prefix;
    }

    /** 관리자 검색어가 접두사까지 붙은 회원 번호면(대소문자 무시) 대문자로 맞춰 돌려준다. "hiu-482913" → "HIU-482913" */
    public static Optional<String> parseFull(String query) {
        if (query == null) {
            return Optional.empty();
        }
        String normalized = query.trim().toUpperCase(Locale.ROOT);
        return FULL.matcher(normalized).matches() ? Optional.of(normalized) : Optional.empty();
    }

    /** 접두사 없이 숫자 6자리만 넣은 검색어인지. "482913" */
    public static boolean isNumberPart(String query) {
        return query != null && DIGITS.matcher(query.trim()).matches();
    }
}
