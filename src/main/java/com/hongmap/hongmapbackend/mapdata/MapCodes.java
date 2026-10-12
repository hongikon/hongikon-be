package com.hongmap.hongmapbackend.mapdata;

import java.security.SecureRandom;

/**
 * 관리자가 id 를 비우고 추가할 때 붙이는 코드: p-xxxxxxxx(제휴업체) / f-xxxxxxxx(편의시설) / pn-xxxxxxxx(경로 중간점).
 * 소문자·숫자 8자. 경로 출입구 노드는 무작위가 아니라 건물 code 와 라벨로 만든다({@link #pathEntrance}).
 */
public final class MapCodes {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String PARTNER_PREFIX = "p-";
    public static final String FACILITY_PREFIX = "f-";
    public static final String PATH_NODE_PREFIX = "pn-";
    public static final String PATH_ENTRANCE_PREFIX = "e-";

    /** code 에 쓸 수 없는 글자(MapDataRules.CODE_REGEX 밖). */
    private static final String INVALID_CODE_CHARS = "[^A-Za-z0-9_-]";
    private static final int MAX_CODE_LENGTH = 100;

    private MapCodes() {
    }

    public static String generate(String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 경로 출입구 노드 code: e-{건물 code}-{라벨}(예: e-hongik_r-HI_R_2F_ENTER). 쓸 수 없는 글자는 '_' 로 바꾼다. */
    public static String pathEntrance(String buildingCode, String label) {
        return toCode(PATH_ENTRANCE_PREFIX + buildingCode + "-" + label);
    }

    /**
     * 아무 문자열을 MapDataRules.CODE_REGEX 에 맞게 바꾼다: 앞뒤 공백 제거, 쓸 수 없는 글자 → '_', 앞쪽 '_'·'-' 제거,
     * 100자로 자름, 비면 "n". 이미 맞으면 그대로다.
     */
    public static String toCode(String raw) {
        String code = raw == null ? "" : raw.trim().replaceAll(INVALID_CODE_CHARS, "_").replaceFirst("^[_-]+", "");
        if (code.length() > MAX_CODE_LENGTH) {
            code = code.substring(0, MAX_CODE_LENGTH);
        }
        return code.isEmpty() ? "n" : code;
    }
}
