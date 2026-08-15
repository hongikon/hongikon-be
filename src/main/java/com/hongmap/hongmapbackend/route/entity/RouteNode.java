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

    // 같은 건물+층 내에서 접속점(계단/엘리베이터/출입구 등)을 구분하기 위한 식별자.
    // 건물 도면 상의 지점 번호를 그대로 사용하므로 자유 형식 varchar로 둔다.
    @Column(name = "point_no", length = 20)
    private String pointNo;

    @Builder
    public RouteNode(BigDecimal latitude, BigDecimal longitude, int floor,
                      String nodeType, Long buildingId, String pointNo) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.floor = floor;
        this.nodeType = nodeType;
        this.buildingId = buildingId;
        this.pointNo = pointNo;
    }
}
