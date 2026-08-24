package com.hongmap.hongmapbackend.building;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * 캠퍼스 건물 정보.
 * 지도 핀 표시 및 소식-장소 키워드 매칭에 사용.
 *
 * DB: buildings (ERD 리뷰 반영본 기준, map_category로 places.category와 네이밍 충돌 회피)
 */
@Entity
@Table(name = "buildings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    /** 건물 슬러그. 예: hongik_k. 데이터 입력 시점에 프론트/입력 규칙에 따라 채워짐 */
    @Column(name = "code", length = 100, unique = true)
    private String code;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    /** 마커 색 HEX (#RRGGBB) */
    @Column(name = "color", length = 9)
    private String color;

    /** 강의 / 식당 / 편의 / 주차 — 지도 필터용. places.category(시설 종류)와는 별개 축 */
    @Column(name = "map_category", length = 20)
    private String mapCategory;

    /** 예: 강의·행정 복합동 (프론트 표시용 설명) */
    @Column(name = "type", length = 50)
    private String type;

    /** 지상 층수. 미확인이면 NULL */
    @Column(name = "floors")
    private Integer floors;

    /** 지하 층수. 없으면 NULL */
    @Column(name = "basement_floors")
    private Integer basementFloors;

    @Column(name = "hours", length = 100)
    private String hours;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "contact", length = 50)
    private String contact;

    /** 문자열 배열 JSON (예: ["엘리베이터", "장애인 화장실"]) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facilities")
    private String facilities;

    @Column(name = "link_label", length = 50)
    private String linkLabel;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** 건물 폴리곤 좌표 배열 JSON [[lat,lng], ...] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "boundary")
    private String boundary;

    /** 미니맵 x좌표 (의미 TODO 확인 필요 — 리뷰본 코멘트 참고) */
    @Column(name = "cx", precision = 6, scale = 2)
    private BigDecimal cx;

    @Column(name = "cy", precision = 6, scale = 2)
    private BigDecimal cy;

    @Column(name = "anchor_x", precision = 6, scale = 2)
    private BigDecimal anchorX;

    @Column(name = "anchor_y", precision = 6, scale = 2)
    private BigDecimal anchorY;
}