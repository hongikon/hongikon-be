package com.hongmap.hongmapbackend.ask;

import java.util.List;

/**
 * 학교 용어 — 학생이 쓰는 말과 학교 안내·공지가 쓰는 말이 다를 때 어떻게 묶어 안내할지(운영진이 알려 준 것만 적는다).
 * 분류·답 쓰기 두 프롬프트에 함께 들어간다. 바꾸면 분류 프롬프트 캐시가 한 번 새로 쓰인다.
 */
final class AskGlossary {

    private AskGlossary() {
    }

    /**
     * 주제별 고정 근거 — 고른 안내 중 하나라도 이 접두어로 시작하면(그 주제의 질문이면) 기준 안내를 꼭 읽히고 출처 맨 위에 둔다.
     * 교환학생은 OUTBOUND 프로그램 안내(oia.hongik.ac.kr/oia/content/6)를 늘 함께 보여야 한다(10-16 운영진).
     */
    record Anchor(List<String> prefixes, String anchorId) {
        boolean matches(String id) {
            return prefixes.stream().anyMatch(prefix -> id.startsWith(prefix));
        }
    }

    static final List<Anchor> ANCHORS = List.of(
            new Anchor(List.of("exchange/", "exchange-partners/", "intl-exchange-"), "intl-exchange-apply/start"));

    static final String PROMPT = """
            학교 용어(학생 말 → 학교 안내에서 묶어 안내하는 법):
            - '교환학생', '교환 가기', '해외 파견' → 국제협력본부 OUTBOUND 프로그램(교류학생).
              첫 문장에서 'OUTBOUND 프로그램'이라는 이름으로 소개하고, 그 안에 교환·방문·복수학위 등이 있다고 종류를 나눠 안내한다.
              OUTBOUND 프로그램 안내를 중심 근거로 삼고, 일정은 'Outbound 교류학생 모집·선발' 공지로 찾는다.
            """;
}
