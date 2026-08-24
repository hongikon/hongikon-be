package com.hongmap.hongmapbackend.building;

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

import java.time.LocalTime;

/**
 * 건물 내 시설 정보 (화장실, 엘리베이터, 정수기 등).
 * category는 buildings.map_category와 값 도메인이 다른 별개 축.
 *
 * DB: places
 */
@Entity
@Table(name = "places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    /** TOILET / ELEVATOR / SMOKING_AREA / WATER_PURIFIER / PRINTER / STAIRS / CONVENIENCE_STORE / RESTAURANT / REST_AREA / ADMIN_OFFICE */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 시설 슬러그. 예: hongik_k_studyroom. 데이터 입력 시점에 프론트/입력 규칙에 따라 채워짐 */
    @Column(name = "code", length = 100, unique = true)
    private String code;

    @Column(name = "floor", nullable = false)
    private Integer floor;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    /** close_time이 익일로 넘어가는 경우(예: 18:00~02:00) true. BETWEEN 조회 시 분기 필요 */
    @Column(name = "is_overnight", nullable = false)
    @Builder.Default
    private Boolean isOvernight = false;

    @Column(name = "extra_info", length = 255)
    private String extraInfo;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}