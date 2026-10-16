package com.hongmap.hongmapbackend.ask.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param question 이번 질문
 * @param hint     앱 낱말 찾기가 고른 안내(흐름/단계, 없으면 비움). 분류에 참고로만 넘기고 카탈로그에 있는 것만 쓴다.
 * @param history  직전 대화(오래된 것부터, 최대 3턴).
 */
public record AskRouteRequest(
        @NotBlank @Size(max = 200, message = "질문은 200자까지 쓸 수 있어요.") String question,
        @Size(max = 200) String hint,
        @Size(max = 3) List<@Valid Turn> history
) {

    /**
     * @param nodeId 그 질문에 열린 안내(흐름/단계). 없었으면 비운다.
     * @param answer 그때 서버가 써 준 답(있으면). '그래서 어떻게 해?' 같은 이어 묻기를 풀 때 쓴다.
     */
    public record Turn(
            @NotBlank @Size(max = 200) String question,
            @Size(max = 200) String nodeId,
            @Size(max = 1000) String answer
    ) {
        public Turn(String question, String nodeId) {
            this(question, nodeId, null);
        }
    }
}
