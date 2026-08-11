package com.hongmap.hongmapbackend.route.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "route_nodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "latitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7, nullable = false)
    private BigDecimal longitude;

    @Column(name = "floor", nullable = false)
    private int floor;

    @Column(name = "node_type", length = 30, nullable = false)
    private String nodeType;

    @Column(name = "building_id")
    private Long buildingId;

    @Builder
    public RouteNode(BigDecimal latitude, BigDecimal longitude, int floor,
                      String nodeType, Long buildingId) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.floor = floor;
        this.nodeType = nodeType;
        this.buildingId = buildingId;
    }
}
