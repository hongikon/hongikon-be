package com.hongmap.hongmapbackend.comment;

import com.hongmap.hongmapbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * 댓글 신고. (comment_id, user_id) 유니크 — 한 사람이 같은 댓글을 두 번 신고할 수 없다. 사유는 제보 신고와 같다.
 *
 * DB: report_comment_flags
 */
@Entity
@Table(name = "report_comment_flags",
        uniqueConstraints = @UniqueConstraint(name = "uq_report_comment_flags", columnNames = {"comment_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportCommentFlag {

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

    /** FALSE_INFO / SPAM / INAPPROPRIATE / PRIVACY / ETC */
    @Column(name = "reason", nullable = false, length = 30)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ReportCommentFlag(ReportComment comment, User user, String reason) {
        this.comment = comment;
        this.user = user;
        this.reason = reason;
    }
}
