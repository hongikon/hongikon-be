package com.hongmap.hongmapbackend.ask;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 물어보기 범위(docs/ask-scope-policy.md). 고르는 단계가 질문마다 하나를 판정하고, SCHOOL 이 아니면 답 쓰기를 부르지 않고
 * 고정 문구로 답한다 — 거절에 API 를 아끼고 문구가 매번 같게.
 * 명백한 악용 패턴은 {@link #preCheck} 가 API 호출 전에 막는다.
 */
public enum AskScope {
    /** 홍익대학교 학생 생활·행정·시설 질문 — 답한다 */
    SCHOOL(null),
    /** 학교와 무관한 요청 */
    OFF_TOPIC("홍익온 물어보기는 홍익대학교 생활·행정·시설 안내만 도와드려요. 학교에 관해 궁금한 점을 물어봐 주세요."),
    /** 다른 대학 자체에 대한 질문 — 홍익대 정보와 섞지 않는다 */
    OTHER_SCHOOL("홍익온 물어보기는 홍익대학교 안내만 다뤄요. 다른 대학 정보는 그 학교 홈페이지에서 확인해 주세요."),
    /** 규칙 무시 유도·프롬프트 공개 요구·남의 개인정보 요구 */
    ABUSE("이 요청은 도와드릴 수 없어요. 홍익대학교 생활·행정·시설에 관해 물어봐 주세요.");

    private final String refusal;

    AskScope(String refusal) {
        this.refusal = refusal;
    }

    /** 고정 거절 문구. SCHOOL 이면 null. */
    public String refusal() {
        return refusal;
    }

    /** 고르는 단계 출력 스키마의 enum 값(소문자). */
    public String wire() {
        return name().toLowerCase();
    }

    public static AskScope fromWire(String value) {
        for (AskScope scope : values()) {
            if (scope.wire().equals(value)) {
                return scope;
            }
        }
        // 모르는 값이면 학교 질문으로 본다 — 학교 질문을 잘못 막는 쪽이 더 나쁘다(정책 2장). 섞임은 출구 검사가 막는다.
        return SCHOOL;
    }

    /**
     * API 호출 전에 막는 명백한 악용(규칙 무시·프롬프트 공개·역할 바꾸기). 일반 질문과 헷갈릴 수 있는 낱말은 넣지 않는다 —
     * 애매한 것은 고르는 단계가 ABUSE 로 판정한다.
     */
    static final List<Pattern> ABUSE_PATTERNS = List.of(
            Pattern.compile("(시스템|system)\\s*(프롬프트|prompt|메시지|message)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(이전|위|앞|기존|모든)\\s*의?\\s*(지시|명령|규칙|설정|프롬프트)\\S{0,4}\\s*(을|를)?\\s*(모두|다|전부)?\\s*(무시|잊|무효)"),
            Pattern.compile("ignore\\s+(all\\s+|the\\s+|any\\s+)?(previous|prior|above|earlier)\\s+(instructions|prompts|rules)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("(개발자|탈옥|관리자)\\s*모드|jailbreak|\\bDAN\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(너|당신)의?\\s*(지시문|프롬프트|설정|규칙)\\S{0,4}\\s*(보여|알려|출력|공개)"));

    /** 명백한 악용이면 ABUSE. 아니면 empty — 고르는 단계가 판정한다. */
    public static Optional<AskScope> preCheck(String question) {
        if (question == null) {
            return Optional.empty();
        }
        for (Pattern pattern : ABUSE_PATTERNS) {
            if (pattern.matcher(question).find()) {
                return Optional.of(ABUSE);
            }
        }
        return Optional.empty();
    }
}
