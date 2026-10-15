package com.hongmap.hongmapbackend.ask;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 챗봇 "물어보기" 질문·피드백 로그(POST /ask/feedback). 못 찾은 질문과 "해결 안 됨" 응답을 모아
 * 안내 흐름 추가와 LLM 도입 여부를 판단하는 익명 통계용이다.
 * 질문 원문에 개인정보가 섞일 수 있어 user_id 는 저장하지 않는다(로그인 여부와 상관없이 같은 행).
 *
 * 앱은 답변 직후 한 번(resolved = null), "궁금한 점이 해결됐나요?"에 응답하면 한 번 더(resolved = true/false) 보낸다.
 * 두 행은 서로 연결하지 않는다 — 해결 비율은 resolved 가 있는 행만으로 센다(docs/ask-feedback-queries.sql).
 *
 * DB: ask_feedback_logs (db/create_ask_feedback_logs_table.sql)
 */
@Entity
@Table(name = "ask_feedback_logs", indexes = {
        @Index(name = "idx_ask_feedback_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_ask_feedback_logs_outcome", columnList = "outcome")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AskFeedbackLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 앞뒤 공백을 뺀 질문(1~200자). */
    @Column(name = "question", nullable = false, length = 200)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private AskOutcome outcome;

    /** outcome 이 FLOW 일 때만 채운다. */
    @Column(name = "flow_id", length = 100)
    private String flowId;

    /** outcome 이 FLOW 일 때만 채운다. */
    @Column(name = "node_id", length = 100)
    private String nodeId;

    /** "해결됐나요?" 응답. 답변 직후 기록이면 null. */
    @Column(name = "resolved")
    private Boolean resolved;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
