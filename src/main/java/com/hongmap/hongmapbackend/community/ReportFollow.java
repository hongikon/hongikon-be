package com.hongmap.hongmapbackend.community;

import com.hongmap.hongmapbackend.report.Report;
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
 * 관심 제보. 로그인 사용자가 남의 공개 제보를 관심 등록하면 시작·곧 끝남 알림과 새 댓글 알림(묶음)을 받는다.
 * start_notified·ending_notified 로 사람마다 한 번만 보낸다. 제보가 끝나거나 공개가 내려가면 스케줄러가 지우고,
 * 제보 삭제·탈퇴 때는 FK ON DELETE CASCADE 로 함께 지워진다.
 *
 * DB: report_follows (db/create_report_community_tables.sql)
 */
@Entity
@Table(name = "report_follows",
        uniqueConstraints = @UniqueConstraint(name = "uq_report_follows", columnNames = {"report_id", "user_id"}),
        indexes = @Index(name = "ix_report_follows_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportFollow {

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

    @Column(name = "start_notified", nullable = false)
    private boolean startNotified;

    @Column(name = "ending_notified", nullable = false)
    private boolean endingNotified;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ReportFollow(Report report, User user) {
        this.report = report;
        this.user = user;
    }

    public void markStartNotified() {
        this.startNotified = true;
    }

    public void markEndingNotified() {
        this.endingNotified = true;
    }
}
