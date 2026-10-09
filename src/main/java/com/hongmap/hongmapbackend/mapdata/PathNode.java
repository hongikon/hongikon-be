package com.hongmap.hongmapbackend.mapdata;

import com.hongmap.hongmapbackend.building.Building;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 경로망의 점. code 는 앱이 쓰는 식별자(프론트 id 그대로, 예: n110)라 GET /map/data 의 paths.nodes[].id 로 나간다.
 * WAYPOINT 는 좌표를 갖고, ENTRANCE 는 (building, entranceLabel) 로 buildings.entrances 의 출입구를 가리킨다 —
 * 좌표는 복사하지 않고 응답을 만들 때 읽는다. 라벨은 JSON 안의 값이라 외래키가 없다(깨진 참조는 path-audit).
 *
 * DB: path_nodes (db/create_path_network_tables.sql). CHECK 는 테스트(H2) 스키마에도 생기도록 여기에도 둔다.
 */
@Entity
@Table(name = "path_nodes", uniqueConstraints = @UniqueConstraint(
        name = "uq_path_nodes_entrance", columnNames = {"building_id", "entrance_label"}))
@Check(name = "ck_path_nodes_kind", constraints =
        "(kind = 'WAYPOINT' AND latitude IS NOT NULL AND longitude IS NOT NULL AND building_id IS NULL AND entrance_label IS NULL)"
        + " OR (kind = 'ENTRANCE' AND building_id IS NOT NULL AND entrance_label IS NOT NULL AND latitude IS NULL AND longitude IS NULL)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PathNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 100, nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false)
    private PathNodeKind kind;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", foreignKey = @ForeignKey(name = "fk_path_nodes_building"))
    private Building building;

    @Column(name = "entrance_label", length = 50)
    private String entranceLabel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PathNode(String code, PathNodeKind kind, BigDecimal latitude, BigDecimal longitude,
                     Building building, String entranceLabel) {
        this.code = code;
        this.kind = kind;
        this.latitude = latitude;
        this.longitude = longitude;
        this.building = building;
        this.entranceLabel = entranceLabel;
    }

    public static PathNode waypoint(String code, BigDecimal latitude, BigDecimal longitude) {
        return new PathNode(code, PathNodeKind.WAYPOINT, latitude, longitude, null, null);
    }

    public static PathNode entrance(String code, Building building, String entranceLabel) {
        return new PathNode(code, PathNodeKind.ENTRANCE, null, null, building, entranceLabel);
    }

    /** 중간점 이동. 출입구 노드는 좌표가 없으므로 부르지 않는다. */
    public void moveTo(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
