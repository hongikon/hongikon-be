package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.building.Building;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 건물 안 편의시설(프린터·열람실·정수기 등). 앱 지도 '편의시설' 층에 그려진다.
 * code 는 앱이 쓰는 식별자(예: hi-r-9f-printer)라 GET /map/data 의 facility.id 로 나간다.
 *
 * DB: campus_facilities (db/alter_map_data_v1.sql). 레거시 places 테이블(분류 체계가 다름)과는 별개.
 */
@Entity
@Table(name = "campus_facilities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampusFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 100, nullable = false, unique = true)
    private String code;

    /** 프린터 / 증명서 발급 / 열람실 … (MapDataRules.FACILITY_KINDS) */
    @Column(name = "kind", length = 20, nullable = false)
    private String kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    /** 층. 지하는 음수. 모르면 NULL */
    @Column(name = "floor")
    private Integer floor;

    @Column(name = "note", length = 255)
    private String note;

    /** 건물 중심과 다른 곳에 핀을 꽂을 때만(둘 다 있거나 둘 다 NULL) */
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public CampusFacility(String code, String kind, Building building, Integer floor, String note,
                          BigDecimal latitude, BigDecimal longitude, Integer sortOrder) {
        this.code = code;
        this.kind = kind;
        this.building = building;
        this.floor = floor;
        this.note = note;
        this.latitude = latitude;
        this.longitude = longitude;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    /** 관리자 편집 — code·sortOrder 는 그대로 둔다. */
    public void update(String kind, Building building, Integer floor, String note,
                       BigDecimal latitude, BigDecimal longitude) {
        this.kind = kind;
        this.building = building;
        this.floor = floor;
        this.note = note;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
