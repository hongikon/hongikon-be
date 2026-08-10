package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.building.Building;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 실시간 제보. 지도의 한 지점(건물·층 포함 가능)에 "지금 진행 중인 일"을 알리는 시간 한정 정보.
 * ends_at이 지나면 지도에서 자동으로 빠짐 (조회 시 status/ends_at 조건으로 필터링).
 *
 * DB: reports (석훈님 제보 기능 API 스펙 제안, 2026-08-10)
 */
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 건물 밖 제보는 NULL. ON DELETE SET NULL */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    /** 층 정보 없으면 NULL */
    @Column(name = "floor")
    private Integer floor;

    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    /** EVENT / PERFORMANCE / FOOD_TRUCK / BOOTH / ETC (석훈님 문서 질문 3 기준 가안) */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", length = 500)
    private String content;

    /** UTC 저장, 표시 시 KST 변환 */
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    /** 이 시각 이후 지도에서 내려감 */
    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    /** ACTIVE / HIDDEN / DELETED */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
