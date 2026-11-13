package com.hongmap.hongmapbackend.walkroute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 길찾기 경로망 한 버전(db/create_walk_graph_versions_table_2026_11_10.sql). 조사 도구가 올리고, 운영에 적용한 하나가 ACTIVE. */
@Entity
@Table(name = "walk_graph_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalkGraphVersion {

    public enum Status { DRAFT, ACTIVE, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "source_name", length = 200)
    private String sourceName;

    @Column(name = "exported_at", length = 40)
    private String exportedAt;

    @Column(name = "vertex_count", nullable = false)
    private int vertexCount;

    @Column(name = "edge_count", nullable = false)
    private int edgeCount;

    /** 서버 길찾기 경로망 JSON(SurveyGraph 모양). */
    @Column(name = "graph", nullable = false, columnDefinition = "LONGTEXT")
    private String graph;

    /** 조사 도구 백업 원본(다시 불러오기용). */
    @Column(name = "backup", columnDefinition = "LONGTEXT")
    private String backup;

    /** 올릴 때 검사·비교 결과 JSON. */
    @Column(name = "report", columnDefinition = "LONGTEXT")
    private String report;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    WalkGraphVersion(String sourceName, String exportedAt, int vertexCount, int edgeCount, String graph, String backup, String report,
                     LocalDateTime createdAt) {
        this.status = Status.DRAFT;
        this.sourceName = sourceName;
        this.exportedAt = exportedAt;
        this.vertexCount = vertexCount;
        this.edgeCount = edgeCount;
        this.graph = graph;
        this.backup = backup;
        this.report = report;
        this.createdAt = createdAt;
    }

    void activate(LocalDateTime now) {
        this.status = Status.ACTIVE;
        this.activatedAt = now;
    }

    void archive() {
        this.status = Status.ARCHIVED;
    }
}
