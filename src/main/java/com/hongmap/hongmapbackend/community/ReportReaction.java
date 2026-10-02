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
 * 제보 🔥(불) 공감. (report_id, user_id) 유니크 — 한 사람이 한 번. 끄면 행을 지운다(누가 눌렀는지는 공개하지 않고 수만 보여 준다).
 * created_at 으로 최근 N분 🔥 수(HOT)를 센다. FK 모두 ON DELETE CASCADE(제보 삭제·탈퇴 시 함께 삭제).
 *
 * DB: report_reactions (db/create_report_community_tables.sql)
 */
@Entity
@Table(name = "report_reactions",
        uniqueConstraints = @UniqueConstraint(name = "uq_report_reactions", columnNames = {"report_id", "user_id"}),
        indexes = {
                @Index(name = "ix_report_reactions_report_created", columnList = "report_id, created_at"),
                @Index(name = "ix_report_reactions_user", columnList = "user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportReaction {

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ReportReaction(Report report, User user) {
        this.report = report;
        this.user = user;
    }
}
