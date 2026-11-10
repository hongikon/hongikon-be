package com.hongmap.hongmapbackend.notice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 앱 공지사항. 관리자가 쓰고 publishAt(게시 시각)부터 앱의 설정 > 공지사항에 보인다.
 * popupUntil 이 있으면 publishAt ~ popupUntil 동안 앱을 켤 때 popupText 를 팝업으로 띄운다(본 사람은 앱이 기억한다).
 * 시각은 모두 UTC(서버 기본 시간대, HongmapBackendApplication).
 *
 * DB: app_notices (db/create_app_notices_table_2026_11_09.sql).
 */
@Entity
@Table(name = "app_notices", indexes = @Index(name = "idx_app_notices_publish_at", columnList = "publish_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "body", length = 5000, nullable = false)
    private String body;

    @Column(name = "publish_at", nullable = false)
    private LocalDateTime publishAt;

    @Column(name = "popup_until")
    private LocalDateTime popupUntil;

    @Column(name = "popup_text", length = 300)
    private String popupText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public AppNotice(String title, String body, LocalDateTime publishAt, LocalDateTime popupUntil, String popupText) {
        this.title = title;
        this.body = body;
        this.publishAt = publishAt;
        this.popupUntil = popupUntil;
        this.popupText = popupText;
    }

    /** 관리자 편집 — 통째로 바꾼다. */
    public void update(String title, String body, LocalDateTime publishAt, LocalDateTime popupUntil, String popupText) {
        this.title = title;
        this.body = body;
        this.publishAt = publishAt;
        this.popupUntil = popupUntil;
        this.popupText = popupText;
    }

    public boolean isPublished(LocalDateTime now) {
        return !publishAt.isAfter(now);
    }

    /** 지금 팝업으로 띄울 때인가 — 게시됐고 popupUntil 전. */
    public boolean isPopupActive(LocalDateTime now) {
        return popupUntil != null && isPublished(now) && now.isBefore(popupUntil);
    }
}
