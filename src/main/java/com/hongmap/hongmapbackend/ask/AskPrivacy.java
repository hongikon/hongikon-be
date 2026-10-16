package com.hongmap.hongmapbackend.ask;

import java.util.regex.Pattern;

/**
 * 물어보기 기록에 넣기 전에 개인정보 형태를 가린다(10-16 운영진 결정: 가려서 저장). 질문 뜻은 남기고 사람을 알아볼 값만 바꾼다.
 * 학교 대표·부서 번호(02-320-…)처럼 공개된 번호는 답에 자주 나와 가리지 않는다 — 휴대전화만 가린다.
 */
final class AskPrivacy {

    private AskPrivacy() {
    }

    private static final Pattern RESIDENT_NO = Pattern.compile("\\d{6}\\s*-\\s*[1-8]\\d{6}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+");
    private static final Pattern MOBILE = Pattern.compile("01[016789][\\s.-]?\\d{3,4}[\\s.-]?\\d{4}");
    /** 학번(영문 한 글자 + 숫자 6~7자리, 예: B612345)과 8자리 이상 숫자열(학번·카드·계좌 번호 등). */
    private static final Pattern STUDENT_ID = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z]\\d{6,7}(?![A-Za-z0-9])");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<!\\d)\\d{8,}(?!\\d)");

    static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = RESIDENT_NO.matcher(text).replaceAll("[주민번호]");
        masked = EMAIL.matcher(masked).replaceAll("[이메일]");
        masked = MOBILE.matcher(masked).replaceAll("[전화번호]");
        masked = STUDENT_ID.matcher(masked).replaceAll("[학번]");
        return LONG_NUMBER.matcher(masked).replaceAll("[번호]");
    }
}
