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
 * 유저별 알림 설정(게시판·카테고리·키워드 외의 알림). 제보 관련 두 가지 + 관리자 알림.
 * 행이 없으면 기본값으로 본다 — 내 제보 결과 알림 켜짐, 캠퍼스 새 제보 알림 꺼짐(스팸 방지). 값을 처음 바꿀 때 행을 만든다.
 *
 * new_report_last_sent_at: 새 제보 푸시 빈도 제한용. 마지막으로 새 제보 푸시를 보낸 시각이며,
 * 이 시각에서 push.report-new-throttle-minutes가 지나지 않은 유저에게는 새 제보 푸시를 보내지 않는다(ReportPushDispatcher).
 * 새 제보 다이제스트(NewReportDigestScheduler)는 이 시각 뒤에 뜬 제보를 모아 보낸다(최대 12시간 전까지).
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
    public static final boolean DEFAULT_ADMIN_ALERTS_ENABLED = true;

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

    /**
     * 관리자 알림(새 제보 승인 대기·새 문의·신고 자동 숨김, AdminAlertDispatcher). role=ADMIN일 때만 의미가 있다.
     * db/alter_user_notification_settings_add_admin_alerts.sql
     */
    @Column(name = "admin_alerts_enabled", nullable = false)
    private boolean adminAlertsEnabled = DEFAULT_ADMIN_ALERTS_ENABLED;

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

    public void changeAdminAlertsEnabled(boolean enabled) {
        this.adminAlertsEnabled = enabled;
    }

    public void changeNewReportsScope(NewReportScope scope) {
        this.newReportsScope = scope;
    }

    /** 캠퍼스 새 제보 알림(일반·다이제스트)을 받는 상태인지 — 켬 + 범위 CAMPUS. */
    public boolean receivesCampusNewReports() {
        return newReportsEnabled && newReportsScope == NewReportScope.CAMPUS;
    }

    /**
     * 캠퍼스 새 제보 알림을 새로 켰을 때(꺼짐·KEYWORDS → 켬·CAMPUS) 마지막 발송 시각을 비운다 — 꺼져 있던 동안 뜬 제보가
     * 다이제스트로 몰려오지 않게(받은 적 없으면 설정을 바꾼 시각 이후 제보만 모은다, NewReportDigestScheduler).
     */
    public void resetNewReportLastSent() {
        this.newReportLastSentAt = null;
    }
}
