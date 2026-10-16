package com.hongmap.hongmapbackend.ask;

/** 한 번의 답에 쓴 토큰(분류 + 답 쓰기 차례들의 합). 기록·비용 확인용. */
public record AskUsage(long input, long output, long cacheRead) {

    public static final AskUsage ZERO = new AskUsage(0, 0, 0);

    AskUsage plus(long input, long output, long cacheRead) {
        return new AskUsage(this.input + input, this.output + output, this.cacheRead + cacheRead);
    }
}
