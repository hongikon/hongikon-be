package com.hongmap.hongmapbackend.mapdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 장소(편의시설 kind '행사·전시')별 전시 일정. 앱 지도 '이벤트 → 전시' 에서 그 장소의 지금·다음 전시로 보인다.
 * facility_code 는 campus_facilities.code 를 가리키지만 외래키는 두지 않는다 — 지도 데이터 동기화 SQL 이
 * campus_facilities 를 지우고 다시 넣기 때문. 없는 장소의 전시는 GET /map/data 에서 빠진다.
 * 날짜는 KST 기준 달력 날짜(시작일·종료일 모두 포함).
 *
 * DB: exhibitions (db/create_exhibitions_table.sql).
 */
@Entity
@Table(name = "exhibitions", indexes = @Index(name = "idx_exhibitions_facility_code", columnList = "facility_code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exhibition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_code", length = 100, nullable = false)
    private String facilityCode;

    @Column(name = "title", length = 150, nullable = false)
    private String title;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "hours", length = 100)
    private String hours;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "link_label", length = 50)
    private String linkLabel;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Exhibition(String facilityCode, String title, LocalDate startsOn, LocalDate endsOn, String hours,
                      String description, String linkLabel, String linkUrl) {
        this.facilityCode = facilityCode;
        this.title = title;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.hours = hours;
        this.description = description;
        this.linkLabel = linkLabel;
        this.linkUrl = linkUrl;
    }

    /** 관리자 편집 — 통째로 바꾼다. */
    public void update(String facilityCode, String title, LocalDate startsOn, LocalDate endsOn, String hours,
                       String description, String linkLabel, String linkUrl) {
        this.facilityCode = facilityCode;
        this.title = title;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.hours = hours;
        this.description = description;
        this.linkLabel = linkLabel;
        this.linkUrl = linkUrl;
    }
}
