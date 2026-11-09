package com.hongmap.hongmapbackend.ask;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 운영진 확인 안내(11-06): 운영진이 관리자 '물어보기 피드백'에서 직접 쓴 질문·답. 물어보기가 비슷한 질문에 답할 때
 * 근거로 함께 읽는다(page:note-{id}). 학교 페이지가 아니라 링크·출처 버튼은 붙이지 않는다.
 * 학생 개인 정보(휴대전화·주민번호·학번)는 저장하지 않는다(저장 전에 거절 — AskPrivacy.personalKind).
 *
 * DB: ask_operator_notes (db/create_ask_operator_notes_2026_11_06.sql)
 */
@Entity
@Table(name = "ask_operator_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AskOperatorNote {

    public static final int QUESTION_MAX = 200;
    public static final int ANSWER_MAX = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "question", nullable = false, length = QUESTION_MAX)
    private String question;

    @Column(name = "answer", nullable = false, length = ANSWER_MAX)
    private String answer;

    /** 이 안내를 만든 계기가 된 답 기록('route:{id}' / 'flow:{clientId}', 선택). */
    @Column(name = "ask_ref", length = 80)
    private String askRef;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** 등록한 운영진(관리자 회원 번호). 학생이 아니다. */
    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AskOperatorNote(String question, String answer, String askRef, Long createdBy) {
        this.question = question;
        this.answer = answer;
        this.askRef = askRef;
        this.createdBy = createdBy;
    }

    public void edit(String question, String answer, Boolean active) {
        if (question != null) {
            this.question = question;
        }
        if (answer != null) {
            this.answer = answer;
        }
        if (active != null) {
            this.active = active;
        }
    }
}
