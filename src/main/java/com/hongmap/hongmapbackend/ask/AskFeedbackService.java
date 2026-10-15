package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskFeedbackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 챗봇 질문·피드백 로그 저장. 누가 보냈는지는 남기지 않는다(AskFeedbackLog). */
@Service
@RequiredArgsConstructor
public class AskFeedbackService {

    static final int QUESTION_MAX_LENGTH = 200;
    static final String QUESTION_BLANK_MESSAGE = "질문을 입력해 주세요.";
    static final String QUESTION_TOO_LONG_MESSAGE = "질문은 200자 이하여야 해요.";

    private final AskFeedbackLogRepository askFeedbackLogRepository;

    /**
     * 질문은 앞뒤 공백을 빼고 1~200자. flowId·nodeId 는 outcome 이 FLOW 일 때만 남기고(그 외에는 버림),
     * 비어 있으면 null 로 저장한다.
     */
    @Transactional
    public void record(AskFeedbackRequest request) {
        String question = request.question() == null ? "" : request.question().strip();
        if (question.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, QUESTION_BLANK_MESSAGE);
        }
        if (question.length() > QUESTION_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, QUESTION_TOO_LONG_MESSAGE);
        }
        boolean flow = request.outcome() == AskOutcome.FLOW;
        askFeedbackLogRepository.save(AskFeedbackLog.builder()
                .question(question)
                .outcome(request.outcome())
                .flowId(flow ? blankToNull(request.flowId()) : null)
                .nodeId(flow ? blankToNull(request.nodeId()) : null)
                .resolved(request.resolved())
                .build());
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
