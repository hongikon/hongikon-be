package com.hongmap.hongmapbackend.user;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 공개 회원 번호 형식: 영문 대문자·숫자 10자리 (예: K7Q2M9XA4D). 36^10(약 3.6×10^15)가지라 추측할 수 없고 거의 겹치지 않는다.
 * 접두사·하이픈 없이 그대로 보여 준다.
 */
public final class MemberCodes {

    public static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static final int LENGTH = 10;

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9]{" + LENGTH + "}");

    private MemberCodes() {
    }

    public static boolean isValid(String code) {
        return code != null && FORMAT.matcher(code).matches();
    }

    /** 관리자 검색어가 회원 번호 형식이면(대소문자 무시) 대문자로 맞춰 돌려준다. " k7q2m9xa4d " → "K7Q2M9XA4D" */
    public static Optional<String> parse(String query) {
        if (query == null) {
            return Optional.empty();
        }
        String normalized = query.trim().toUpperCase(Locale.ROOT);
        return isValid(normalized) ? Optional.of(normalized) : Optional.empty();
    }
}
