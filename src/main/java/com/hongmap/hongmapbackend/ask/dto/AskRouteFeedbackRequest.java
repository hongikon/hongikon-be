package com.hongmap.hongmapbackend.ask.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * POST /ask/route/feedback — 서버 답 기록에 '궁금한 점이 해결됐나요?' 응답을 붙인다.
 *
 * @param logId          /ask/route 응답의 logId
 * @param conversationId 그 질문을 보낸 대화 id(같아야 바꿀 수 있다)
 */
public record AskRouteFeedbackRequest(
        @NotNull Long logId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{8,64}") String conversationId,
        @NotNull Boolean resolved
) {
}
