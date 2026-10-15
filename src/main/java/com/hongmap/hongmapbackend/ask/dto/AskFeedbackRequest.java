package com.hongmap.hongmapbackend.ask.dto;

import com.hongmap.hongmapbackend.ask.AskOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /ask/feedback 본문. question 의 200자 제한은 앞뒤 공백을 뺀 길이라 서비스에서 본다
 * (여기서는 지나치게 긴 본문만 막는다).
 */
public record AskFeedbackRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 1000, message = "질문은 200자 이하여야 해요.")
        String question,
        @NotNull(message = "outcome 은 FLOW, OFFICES, NONE 중 하나여야 해요.")
        AskOutcome outcome,
        @Size(max = 100, message = "flowId 는 100자 이하여야 해요.")
        String flowId,
        @Size(max = 100, message = "nodeId 는 100자 이하여야 해요.")
        String nodeId,
        Boolean resolved
) {
}
