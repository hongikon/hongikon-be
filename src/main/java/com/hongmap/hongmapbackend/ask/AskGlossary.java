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
            - '○학년부터 다시 다니고 싶어', '학년을 다시 다니기', '○학기부터 다시'(재학·휴학 중인 학생) → 학기재수강
              (지우려는 학기부터 직전 학기까지 성적을 지우고 같은 학년·학기로 돌아가 다시 이수, semester-retake).
              재입학(readmission)은 자퇴·제적으로 학적이 없어진 학생만 한다 — 학생이 자퇴·제적을 말하지 않았으면 재입학으로 답하지 않는다.
            - 알파벳 건물 이름을 한글 발음으로 쓴 말 → 그 알파벳 동(쥐동·지동=G동, 티동=T동, 씨동=C동, 알동=R동, 큐동=Q동, 케이동=K동, 에프동=F동).
            - '중동' → 중앙동아리, '가동' → 가동아리, '동방' → 동아리방, '과사' → 학과 사무실, '졸프' → 졸업 프로젝트(졸업작품·종합설계),
              '컴밤' → '컴공인의 밤'(컴퓨터공학과 행사 — 학과 공지로 찾는다), '과잠' → 학과 단체 점퍼(학과 학생회 공지로 찾는다).
            """;
}
