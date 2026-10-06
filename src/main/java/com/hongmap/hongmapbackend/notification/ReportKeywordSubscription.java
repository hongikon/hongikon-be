package com.hongmap.hongmapbackend.notification;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 제보 전용 키워드 알림 구독. 소식 키워드(KeywordSubscription, keyword_subscriptions)와 별개다.
 * 새 제보가 지도에 올라갈 때 제목·본문·장소 설명·직접 입력 분류·건물명에 keyword가 들어 있으면(대소문자·공백 무시)
 * 새 제보 알림을 켠 유저에게 빈도 제한 없이 먼저 보낸다(ReportPushDispatcher). 개인 설정이라 본인 것만 조회·삭제된다.
 *
 * DB: report_keyword_subscriptions (UNIQUE user_id, keyword) — db/create_report_keyword_subscriptions.sql
 */
@Entity
@Table(name = "report_keyword_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uq_report_keyword_user_keyword", columnNames = {"user_id", "keyword"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReportKeywordSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "keyword", nullable = false, length = 30)
    private String keyword;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
