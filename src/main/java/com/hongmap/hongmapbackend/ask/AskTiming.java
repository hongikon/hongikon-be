package com.hongmap.hongmapbackend.ask;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 번의 답에서 단계별로 걸린 시간(ms) — 어느 단계가 느린지 보려고 기록(ask_conversation_logs.steps)에 한 줄로 남긴다.
 * 거치지 않은 단계(RAG 꺼짐, 거절로 답 쓰기 없음, 실패로 중간에 끝남)는 줄에서 뺀다.
 */
final class AskTiming {

    static final String PREFIX = "시간: ";

    private Long embedMs;
    private Long searchMs;
    private Long classifyMs;
    private Integer toolRounds;
    private long toolMs;
    private Long firstTextMs;
    private Long answerMs;

    void retrieval(long embedMs, long searchMs) {
        this.embedMs = embedMs;
        this.searchMs = searchMs;
    }

    void classify(long ms) {
        this.classifyMs = ms;
    }

    /** firstTextMs 는 답 쓰기 시작부터 첫 text delta 까지(글자가 하나도 안 나왔으면 음수). */
    void answer(int toolRounds, long toolMs, long firstTextMs, long totalMs) {
        this.toolRounds = toolRounds;
        this.toolMs = toolMs;
        this.firstTextMs = firstTextMs < 0 ? null : firstTextMs;
        this.answerMs = totalMs;
    }

    /** 예: "시간: 임베딩 120ms · 색인 35ms · 고르기 2100ms · 도구 2회 1800ms · 첫 글자 5400ms · 답 쓰기 8200ms · 전체 10500ms" */
    String line(long totalMs) {
        List<String> parts = new ArrayList<>();
        if (embedMs != null) {
            parts.add("임베딩 " + embedMs + "ms");
            parts.add("색인 " + searchMs + "ms");
        }
        if (classifyMs != null) {
            parts.add("고르기 " + classifyMs + "ms");
        }
        if (answerMs != null) {
            parts.add("도구 " + toolRounds + "회 " + toolMs + "ms");
            if (firstTextMs != null) {
                parts.add("첫 글자 " + firstTextMs + "ms");
            }
            parts.add("답 쓰기 " + answerMs + "ms");
        }
        parts.add("전체 " + totalMs + "ms");
        return PREFIX + String.join(" · ", parts);
    }
}
