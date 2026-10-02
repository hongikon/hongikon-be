package com.hongmap.hongmapbackend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 유저별 알림 설정(게시판·카테고리·키워드 외의 알림). 지금은 제보 관련 두 가지.
 * 행이 없으면 기본값으로 본다 — 내 제보 결과 알림 켜짐, 캠퍼스 새 제보 알림 꺼짐(스팸 방지). 값을 처음 바꿀 때 행을 만든다.
 *
 * new_report_last_sent_at: 새 제보 푸시 빈도 제한용. 마지막으로 새 제보 푸시를 보낸 시각이며,
 * 이 시각에서 push.report-new-throttle-minutes가 지나지 않은 유저에게는 새 제보 푸시를 보내지 않는다(ReportPushDispatcher).
 *
 * DB: user_notification_settings (PK user_id / INDEX new_reports_enabled, new_report_last_sent_at)
 * — db/create_user_notification_settings.sql
 */
@Entity
@Table(
        name = "user_notification_settings",
        indexes = @Index(name = "ix_notif_setting_new_reports", columnList = "new_reports_enabled, new_report_last_sent_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationSetting {

    public static final boolean DEFAULT_REPORT_STATUS_ENABLED = true;
    public static final boolean DEFAULT_NEW_REPORTS_ENABLED = false;

    /** users.id. 유저당 한 행이라 PK로 쓴다(회원탈퇴 시 UserService에서 지우고, DB에도 ON DELETE CASCADE). */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /** 내가 올린 제보가 승인·반려됐을 때 알림 */
    @Column(name = "report_status_enabled", nullable = false)
    private boolean reportStatusEnabled = DEFAULT_REPORT_STATUS_ENABLED;

    /** 다른 사람의 제보가 지도에 새로 올라왔을 때 알림 */
    @Column(name = "new_reports_enabled", nullable = false)
    private boolean newReportsEnabled = DEFAULT_NEW_REPORTS_ENABLED;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_reports_scope", nullable = false, length = 20)
    private NewReportScope newReportsScope = NewReportScope.CAMPUS;

    @Column(name = "new_report_last_sent_at")
    private LocalDateTime newReportLastSentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserNotificationSetting(Long userId) {
        this.userId = userId;
    }

    public void changeReportStatusEnabled(boolean enabled) {
        this.reportStatusEnabled = enabled;
    }

    public void changeNewReportsEnabled(boolean enabled) {
        this.newReportsEnabled = enabled;
    }

    public void changeNewReportsScope(NewReportScope scope) {
        this.newReportsScope = scope;
    }
}
