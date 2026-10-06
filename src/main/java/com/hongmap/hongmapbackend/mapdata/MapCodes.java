package com.hongmap.hongmapbackend.mapdata;

import java.security.SecureRandom;

/** 관리자가 id 를 비우고 추가할 때 붙이는 코드: p-xxxxxxxx(제휴업체) / f-xxxxxxxx(편의시설). 소문자·숫자 8자. */
public final class MapCodes {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String PARTNER_PREFIX = "p-";
    public static final String FACILITY_PREFIX = "f-";

    private MapCodes() {
    }

    public static String generate(String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
