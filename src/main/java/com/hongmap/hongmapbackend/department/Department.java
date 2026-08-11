package com.hongmap.hongmapbackend.department;

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

/**
 * 공지 출처(학과+행정기관 통칭). kind로 학과/행정기관 구분.
 */
@Entity
@Table(name = "departments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 앱 TREE_DATA 상위 그룹명 (예: 공과대학) */
    @Column(name = "college", nullable = false, length = 100)
    private String college;

    /** department / office — 학과·행정기관 구분 */
    @Column(name = "kind", nullable = false, length = 20)
    @Builder.Default
    private String kind = "department";
}
