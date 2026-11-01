package com.hongmap.hongmapbackend.ask;

/**
 * 한 번의 답에 쓴 토큰(분류 + 답 쓰기 차례들의 합). 기록·비용 확인용.
 * input 은 캐시 쓰기를 포함한 입력(ask_conversation_logs.input_tokens 와 같은 뜻), cacheWrite 는 그중 캐시에 새로 쓴 몫
 * — 컬럼을 늘리지 않으려고(운영 ddl-auto=validate) 기록 steps 의 '토큰:' 줄에만 남긴다.
 */
public record AskUsage(long input, long output, long cacheRead, long cacheWrite) {

    public static final AskUsage ZERO = new AskUsage(0, 0, 0, 0);
    static final String PREFIX = "토큰: ";

    /** uncached 는 캐시를 안 쓴 입력, cacheWrite 는 캐시에 새로 쓴 입력(API usage 그대로). */
    AskUsage plus(long uncached, long output, long cacheRead, long cacheWrite) {
        return new AskUsage(this.input + uncached + cacheWrite, this.output + output, this.cacheRead + cacheRead,
                this.cacheWrite + cacheWrite);
    }

    /** 예: "토큰: 입력 8200 · 캐시 쓰기 0 · 캐시 읽기 94000 · 출력 1500" (입력은 캐시 쓰기를 뺀 값). */
    String line() {
        return PREFIX + "입력 " + (input - cacheWrite) + " · 캐시 쓰기 " + cacheWrite + " · 캐시 읽기 " + cacheRead + " · 출력 " + output;
    }
}
