package com.hongmap.hongmapbackend.ask;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 문 닫은 교내 시설(11-12 사용자 피드백). 지도 데이터에서는 SQL 로 지우지만(db/sync_facilities_2026_11_12_feedback.sql),
 * 안내 원문(ask/sources.json — FE 에서 내보낸 학교 홈페이지 글)과 학식 메뉴 식당 목록에는 이름이 남아 있어 답에 '운영 중'으로 섞일 수 있다.
 * 원문을 다시 내보내고 색인을 다시 만들 때까지 답 쓰기에 이 목록을 함께 넘겨 막는다.
 */
final class AskClosedPlaces {

    /** 닫은 곳 — 이름, 위치, 근거. */
    record Closed(String name, String where, String since) {
    }

    static final List<Closed> ALL = List.of(
            new Closed("파브리카", "홍문관 R동 2층 카페", "운영진 확인(11-12)"),
            new Closed("교직원식당", "문헌관 MH동 16층 식당", "2026-10-31 운영 종료(위탁운영 계약 만료)"));

    private static final Pattern NAMES = Pattern.compile(String.join("|", ALL.stream().map(Closed::name).toList()));

    private AskClosedPlaces() {
    }

    /** 답 쓰기 메시지에 붙일 줄. */
    static String contextLine() {
        StringBuilder line = new StringBuilder("[문 닫은 곳] ");
        for (int i = 0; i < ALL.size(); i++) {
            Closed closed = ALL.get(i);
            line.append(i == 0 ? "" : ", ").append(closed.name()).append("(").append(closed.where()).append(" — ").append(closed.since()).append(")");
        }
        return line.append(". 안내 원문·공지·메뉴에 나와도 지금 운영하는 곳으로 쓰지 않는다 — 목록·표에 넣지 않고, 학생이 그곳을 물으면 문을 닫았다고 답한다.\n\n")
                .toString();
    }

    /** 이름에 닫은 곳 이름이 들었나(학식 메뉴 식당 이름 등). */
    static boolean mentions(String name) {
        return name != null && NAMES.matcher(name).find();
    }
}
