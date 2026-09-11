package com.hongmap.hongmapbackend.report;

import com.hongmap.hongmapbackend.building.Building;
import com.hongmap.hongmapbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 실시간 제보. 로그인 유저가 지도의 특정 지점(건물+층)에 올리는 시간 한정 이벤트 정보.
 * ends_at이 지나면 지도에서 자동으로 빠짐 (조회 시 status/ends_at 조건으로 필터링).
 *
 * DB: reports
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

    /** 이벤트 정보가 위치한 건물. 층 단위 정보이므로 필수 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    /** 건물 내 층. 층 단위가 이 기능의 핵심 정보라 필수 */
    @Column(name = "floor", nullable = false)
    private Integer floor;

    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ReportCategory category;

    /** category가 ETC일 때만 사용하는 자유 텍스트 세분화 라벨 */
    @Column(name = "custom_category_label", length = 50)
    private String customCategoryLabel;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
