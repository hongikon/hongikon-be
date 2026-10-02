package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 댓글·답글 👍. (comment_id, user_id) 유니크 — 한 사람이 한 번, 끄면 행 삭제. 누가 눌렀는지는 공개하지 않는다(수와 "내가 눌렀는지"만).
 * FK 모두 ON DELETE CASCADE(댓글 행 삭제·탈퇴 시 함께 삭제).
 *
 * DB: report_comment_likes (db/create_report_community_tables.sql)
 */
@Entity
@Table(name = "report_comment_likes",
        uniqueConstraints = @UniqueConstraint(name = "uq_report_comment_likes", columnNames = {"comment_id", "user_id"}),
        indexes = @Index(name = "ix_report_comment_likes_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportCommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ReportComment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ReportCommentLike(ReportComment comment, User user) {
        this.comment = comment;
        this.user = user;
    }
}
