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

/**
 * 자유 키워드 알림 구독. 제목에 keyword가 포함된 소식이 올라오면 알림.
 * [가정 B 확인됨] 카테고리 설정(NotificationCategory)·게시판 구독(UserBoardSubscription)과 별개인 "자유 키워드" 구독.
 * 게시판 구독·카테고리 설정과 무관하게 제목에 keyword가 포함되면(대소문자 무시) 추가로 푸시 대상이 된다(NewsPushDispatcher).
 *
 * DB: keyword_subscriptions (UNIQUE user_id, keyword — 운영 콜레이션 utf8mb4_unicode_ci 라 대소문자 무시로 유일).
 * 엔티티에도 적어 H2 테스트 스키마에 걸리게 한다. 운영에 없으면 db/alter_add_unique_user_lists.sql 이 만든다.
 */
@Entity
@Table(name = "keyword_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uq_keyword_user_keyword", columnNames = {"user_id", "keyword"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class KeywordSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "keyword", nullable = false, length = 30)
    private String keyword;
}
