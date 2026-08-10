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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자유 키워드 알림 구독. 제목에 keyword가 포함된 소식이 올라오면 알림.
 * [가정 B 확인 필요] 카테고리 구독(NotificationCategory)·학과 구독(UserDepartment)과 겹치지 않는
 * "자유 키워드"로 해석. 원래 의도가 카테고리였다면 이 엔티티는 삭제 대상.
 *
 * DB: keyword_subscriptions (UNIQUE user_id, keyword)
 */
@Entity
@Table(name = "keyword_subscriptions")
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
