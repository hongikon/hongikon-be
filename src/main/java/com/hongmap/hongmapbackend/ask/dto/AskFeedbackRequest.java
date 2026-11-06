package com.hongmap.hongmapbackend.ask.dto;

import com.hongmap.hongmapbackend.ask.AskOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        Boolean resolved,
        /** 이 '해결됐나요' 한 번의 임의 값(선택, 11-06). 같은 값으로 다시 보내면 새 행 대신 그 행을 고친다. */
        @Pattern(regexp = "[A-Za-z0-9-]{8,64}", message = "clientId 형식이 맞지 않아요.") String clientId,
        /** '해결 안 됨'의 이유(선택): WRONG·OUTDATED·OFF_TOPIC·NOT_ENOUGH. 모르는 값은 버린다. */
        @Size(max = 20) String reason
) {
    /** 옛 앱 본문(clientId·reason 없음). */
    public AskFeedbackRequest(String question, AskOutcome outcome, String flowId, String nodeId, Boolean resolved) {
        this(question, outcome, flowId, nodeId, resolved, null, null);
    }
}
