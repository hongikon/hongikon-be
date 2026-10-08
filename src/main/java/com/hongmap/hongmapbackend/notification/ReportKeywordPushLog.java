package com.hongmap.hongmapbackend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 제보 키워드 알림 발송 기록 — 같은 제보로 같은 유저에게 키워드 알림이 두 번 가지 않게 한다
 * (예정 제보 스케줄러가 같은 제보를 다시 집어도). (report_id, user_id) 유일 제약에 먼저 INSERT 한 쪽만 보낸다.
 * 같은 제보의 캠퍼스 새 제보 알림도 이 기록에 있는 유저는 뺀다.
 * 키워드 내용은 남기지 않는다(개인 설정). 제보·유저가 지워지면 DB FK(ON DELETE CASCADE)로 같이 지워진다.
 *
 * DB: report_keyword_push_log — db/create_report_keyword_subscriptions.sql
 */
@Entity
@Table(name = "report_keyword_push_log",
        uniqueConstraints = @UniqueConstraint(name = "uq_report_kw_push_report_user", columnNames = {"report_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportKeywordPushLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ReportKeywordPushLog(Long reportId, Long userId) {
        this.reportId = reportId;
        this.userId = userId;
    }
}
