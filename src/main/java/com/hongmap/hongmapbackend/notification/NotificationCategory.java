package com.hongmap.hongmapbackend.notification;

import com.hongmap.hongmapbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 유저별 알림 카테고리 구독 여부. PK가 (user_id, category) 복합키라 IdClass 사용.
 * 행 기반 설계라 카테고리 추가 시 ALTER TABLE 없이 INSERT만으로 확장 가능.
 * [가정 A 확인됨] category 값은 앱 7종(공지·장학·행사·수강·시설·취업·상담) — News.category(NewsCategoryClassifier)와 같은 값.
 * 대학공지(News.department 없음)가 올라오면 그 category를 끄지 않은(enabled = false 행이 없는) 유저에게 푸시한다(NewsPushDispatcher).
 * 행이 없으면 켜짐 — 화면(NotificationCategoryService.getUserCategories)과 같은 기준.
 *
 * DB: notification_categories
 */
@Entity
@Table(name = "notification_categories")
@IdClass(NotificationCategory.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class NotificationCategory {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @Column(name = "category", length = 30)
    private String category;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private Long user;
        private String category;
    }
}
