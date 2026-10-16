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
 * 물어보기 서버 답 기록(POST /ask/route·/ask/route/stream 한 번에 한 행) — 실제 질문·답·근거·살펴본 과정·비용을 모아
 * 못 찾은 질문과 '해결 안 됨'을 보고 안내·프롬프트를 고치고 회귀 평가셋을 만드는 데 쓴다(docs/ask-conversation-queries.sql).
 *
 * 개인정보: 질문·답·과정은 저장 전에 가린다(AskPrivacy — 학번·휴대전화·이메일·주민번호). user_id·IP 는 두지 않는다.
 * conversation_id 는 앱 탭마다 새로 만드는 임의 값으로 같은 대화의 질문끼리만 잇는다. 1년 지나면 지운다(AskConversationRecorder).
 * 앱 화면에서 보는 '물어보기'의 키워드 통계(ask_feedback_logs)와는 따로다.
 *
 * DB: ask_conversation_logs (db/create_ask_conversation_logs_table.sql)
 */
@Entity
@Table(name = "ask_conversation_logs", indexes = {
        @Index(name = "idx_ask_conversation_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_ask_conversation_logs_conversation", columnList = "conversation_id"),
        @Index(name = "idx_ask_conversation_logs_outcome", columnList = "outcome")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AskConversationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 앱 탭마다 만든 임의 대화 id(없으면 null). */
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    /** 대화에서 몇 번째 질문인지(직전 대화 턴 수 + 1). */
    @Column(name = "turn", nullable = false)
    private int turn;

    /** 가린 질문. */
    @Column(name = "question", nullable = false, length = 200)
    private String question;

    /** 가린 답(서버가 답을 쓰지 않았으면 null). */
    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private Outcome outcome;

    /** 근거 안내 id(쉼표로). */
    @Column(name = "node_ids", length = 1000)
    private String nodeIds;

    /** 근거로 쓴 공지·학식 제목(줄바꿈으로). */
    @Column(name = "reference_titles", columnDefinition = "TEXT")
    private String referenceTitles;

    /** 살펴본 과정(도구·검색어, 줄바꿈으로, 가림). */
    @Column(name = "steps", columnDefinition = "TEXT")
    private String steps;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private long cacheReadTokens;

    @Column(name = "catalog_version", length = 20)
    private String catalogVersion;

    /** '궁금한 점이 해결됐나요?' 응답(아직이면 null). */
    @Column(name = "resolved")
    private Boolean resolved;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void resolve(boolean resolved) {
        this.resolved = resolved;
    }

    public enum Outcome {
        /** 안내 원문에서 확인한 답 */
        ANSWERED,
        /** 답은 썼지만 확인되지 않은 내용이 있음(found=false) */
        NOT_FOUND,
        /** 되묻기 단계(버튼)로 넘김 */
        QUESTION,
        /** 맞는 안내 없음·답 쓰기 실패로 원문만 보냄 */
        NO_ANSWER,
        /** 서버가 분류하지 못함(503) */
        FAILED,
        /** 학생이 멈춤을 눌러 끊김 */
        STOPPED
    }
}
