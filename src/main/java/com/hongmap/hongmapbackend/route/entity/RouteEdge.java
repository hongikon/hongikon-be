package com.hongmap.hongmapbackend.route.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "route_edges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_node_id", nullable = false)
    private Long fromNodeId;

    @Column(name = "to_node_id", nullable = false)
    private Long toNodeId;

    @Column(name = "distance_m", precision = 6, scale = 2, nullable = false)
    private BigDecimal distanceM;

    @Column(name = "has_roof", nullable = false)
    private boolean hasRoof;

    @Column(name = "is_barrier_free", nullable = false)
    private boolean isBarrierFree;

    @Column(name = "edge_type", length = 30, nullable = false)
    private String edgeType;

    @Builder
    public RouteEdge(Long fromNodeId, Long toNodeId, BigDecimal distanceM,
                      boolean hasRoof, boolean isBarrierFree, String edgeType) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.distanceM = distanceM;
        this.hasRoof = hasRoof;
        this.isBarrierFree = isBarrierFree;
        this.edgeType = edgeType;
    }
}
