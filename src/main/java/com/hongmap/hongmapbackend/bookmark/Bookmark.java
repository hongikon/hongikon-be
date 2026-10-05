package com.hongmap.hongmapbackend.bookmark;

import com.hongmap.hongmapbackend.news.News;
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
 * 유저-소식 북마크. (user_id, news_id) 조합으로 유일 — 운영 DB 에 UNIQUE(user_id, news_id) 가 있다
 * (db/cleanup_arch_duplicate_news.sql 참고, 없으면 db/alter_add_unique_user_lists.sql 이 만든다).
 * 엔티티에도 적어 H2 테스트 스키마에 걸리게 하고, 서비스는 "이미 있으면 그대로 돌려줌" + 경합 재시도로 멱등하게 만든다.
 */
@Entity
@Table(name = "bookmarks",
        uniqueConstraints = @UniqueConstraint(name = "uq_bookmark_user_news", columnNames = {"user_id", "news_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id", nullable = false)
    private News news;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
