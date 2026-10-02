package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.report.Report;
import com.hongmap.hongmapbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 제보 댓글. 로그인 사용자가 지도에 공개된(ACTIVE) 제보에 남긴다. 작성 즉시 공개되고(사후 검토), 신고가 쌓이면 자동으로 숨긴다.
 *
 * <p>FK 는 둘 다 ON DELETE CASCADE — 제보가 지워지면(작성자 삭제·탈퇴) 댓글도, 댓글 작성자가 탈퇴하면 그 사람의 댓글도
 * DB 가 함께 지운다(개인정보 최소 보관). 그래서 UserService.withdraw 에 정리 코드를 따로 두지 않는다.
 *
 * DB: report_comments (db/create_report_comments_table.sql)
 */
@Entity
@Table(name = "report_comments", indexes = {
        @Index(name = "ix_report_comments_report", columnList = "report_id, status, id"),
        @Index(name = "ix_report_comments_user_created", columnList = "user_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportComment {

    /** 댓글 최대 글자 수(공백 제거 후, 코드포인트가 아니라 Java char 기준 — 컬럼 VARCHAR(200)과 맞춘다). */
    public static final int MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "content", nullable = false, length = MAX_LENGTH)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportCommentStatus status = ReportCommentStatus.VISIBLE;

    /** 관리자가 마지막으로 상태를 바꾼 시각. 자동 숨김은 이 시각 뒤에 들어온 신고만 센다(복원한 댓글을 옛 신고로 다시 숨기지 않음). */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ReportComment(Report report, User user, String content) {
        this.report = report;
        this.user = user;
        this.content = content;
    }

    public void deleteByAuthor() {
        this.status = ReportCommentStatus.DELETED;
    }

    public void moderate(ReportCommentStatus status, LocalDateTime reviewedAt) {
        this.status = status;
        this.reviewedAt = reviewedAt;
    }
}
