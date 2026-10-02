package com.hongmap.hongmapbackend.notification;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 유저별 게시판 구독. source_id는 크롤러 게시판의 BoardConfig.sourceId(= news.source_id) —
 * 학과 게시판이면 학과명(예: "컴퓨터공학과"), 대학공지면 분류 라벨(예: "학사", "장학").
 * 새 소식은 그 게시판을 구독하고 alert_enabled = true인 유저에게만 푸시된다(UserDeviceRepository.findPushTargets).
 * 구독은 유지하되 알림만 끄고 싶을 때 alert_enabled = false.
 *
 * DB: user_board_subscriptions (UNIQUE user_id, source_id / INDEX source_id, alert_enabled)
 * — db/create_user_board_subscriptions.sql
 */
@Entity
@Table(
        name = "user_board_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uq_board_sub_user_source", columnNames = {"user_id", "source_id"}),
        indexes = @Index(name = "ix_board_sub_source_alert", columnList = "source_id, alert_enabled")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBoardSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "source_id", nullable = false, length = 50)
    private String sourceId;

    @Column(name = "alert_enabled", nullable = false)
    private boolean alertEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public UserBoardSubscription(User user, String sourceId, boolean alertEnabled) {
        this.user = user;
        this.sourceId = sourceId;
        this.alertEnabled = alertEnabled;
    }

    public void changeAlertEnabled(boolean alertEnabled) {
        this.alertEnabled = alertEnabled;
    }
}
