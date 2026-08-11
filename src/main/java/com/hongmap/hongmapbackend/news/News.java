package com.hongmap.hongmapbackend.news;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.department.Department;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 크롤링 소식. source_url UNIQUE — 크롤러 재실행 시 중복 저장 방지.
 * department_id는 Department 도메인 완성으로 @ManyToOne 관계로 승격됨 (2026-08-11).
 */
@Entity
@Table(name = "news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "source_url", nullable = false, unique = true, length = 500)
    private String sourceUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "crawled_at", nullable = false, updatable = false)
    private LocalDateTime crawledAt;
}
