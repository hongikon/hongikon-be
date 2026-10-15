package com.hongmap.hongmapbackend.ask;

import com.hongmap.hongmapbackend.ask.dto.AskFeedbackRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** AskFeedbackService 단위 테스트 — 저장소는 목. */
class AskFeedbackServiceTest {

    private AskFeedbackLogRepository repository;
    private AskFeedbackService service;

    @BeforeEach
    void setUp() {
        repository = mock(AskFeedbackLogRepository.class);
        service = new AskFeedbackService(repository);
    }

    private AskFeedbackLog saved() {
        ArgumentCaptor<AskFeedbackLog> captor = ArgumentCaptor.forClass(AskFeedbackLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void FLOW_답변은_질문_앞뒤_공백을_빼고_흐름_노드와_함께_저장한다() {
        service.record(new AskFeedbackRequest("  휴학 신청 기간 ", AskOutcome.FLOW,
                " leave-of-absence ", "general", true));

        AskFeedbackLog log = saved();
        assertThat(log.getQuestion()).isEqualTo("휴학 신청 기간");
        assertThat(log.getOutcome()).isEqualTo(AskOutcome.FLOW);
        assertThat(log.getFlowId()).isEqualTo("leave-of-absence");
        assertThat(log.getNodeId()).isEqualTo("general");
        assertThat(log.getResolved()).isTrue();
    }

    @Test
    void 답변_직후_기록은_resolved_가_null_로_남는다() {
        service.record(new AskFeedbackRequest("장학금", AskOutcome.FLOW, "scholarship", null, null));

        AskFeedbackLog log = saved();
        assertThat(log.getResolved()).isNull();
        assertThat(log.getNodeId()).isNull();
    }

    @Test
    void FLOW_가_아니면_flowId_nodeId_를_버린다() {
        service.record(new AskFeedbackRequest("학생처 전화번호", AskOutcome.OFFICES, "leave-of-absence", "general", false));

        AskFeedbackLog log = saved();
        assertThat(log.getOutcome()).isEqualTo(AskOutcome.OFFICES);
        assertThat(log.getFlowId()).isNull();
        assertThat(log.getNodeId()).isNull();
        assertThat(log.getResolved()).isFalse();
    }

    @Test
    void 빈_flowId_는_null_로_저장한다() {
        service.record(new AskFeedbackRequest("휴학", AskOutcome.FLOW, "   ", "", null));

        AskFeedbackLog log = saved();
        assertThat(log.getFlowId()).isNull();
        assertThat(log.getNodeId()).isNull();
    }

    @Test
    void 공백을_빼고_200자면_받는다() {
        String ok = "가".repeat(200);
        service.record(new AskFeedbackRequest("  " + ok + "  ", AskOutcome.NONE, null, null, null));
        assertThat(saved().getQuestion()).isEqualTo(ok);
    }

    @Test
    void 공백을_빼고_201자면_400() {
        assertThatThrownBy(() -> service.record(new AskFeedbackRequest("가".repeat(201), AskOutcome.NONE, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo(AskFeedbackService.QUESTION_TOO_LONG_MESSAGE);
                });
        verify(repository, never()).save(any());
    }

    @Test
    void 공백뿐인_질문은_400() {
        assertThatThrownBy(() -> service.record(new AskFeedbackRequest(" \t ", AskOutcome.NONE, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }
}
