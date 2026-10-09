package com.hongmap.hongmapbackend.mapdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 경로망의 양방향 간선 한 줄. 항상 nodeA.id < nodeB.id 로 저장한다({@link #between}) — A–B 와 B–A 가 따로 들어가지 않고
 * 자기 자신 연결도 막힌다. 거리는 저장하지 않고 좌표로 계산한다.
 *
 * DB: path_edges (db/create_path_network_tables.sql). 이어진 간선이 있으면 노드를 지울 수 없다(FK RESTRICT).
 */
@Entity
@Table(name = "path_edges",
        uniqueConstraints = @UniqueConstraint(name = "uq_path_edges_pair", columnNames = {"node_a_id", "node_b_id"}),
        indexes = @Index(name = "idx_path_edges_node_b", columnList = "node_b_id"))
@Check(name = "ck_path_edges_order", constraints = "node_a_id < node_b_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PathEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_a_id", nullable = false, foreignKey = @ForeignKey(name = "fk_path_edges_node_a"))
    private PathNode nodeA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_b_id", nullable = false, foreignKey = @ForeignKey(name = "fk_path_edges_node_b"))
    private PathNode nodeB;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private PathEdge(PathNode nodeA, PathNode nodeB) {
        this.nodeA = nodeA;
        this.nodeB = nodeB;
    }

    /** 두 노드(저장된 것)를 잇는 간선. id 가 작은 쪽을 A 로 둔다. 같은 노드면 IllegalArgumentException. */
    public static PathEdge between(PathNode x, PathNode y) {
        if (x.getId() == null || y.getId() == null) {
            throw new IllegalArgumentException("저장된 노드만 이을 수 있습니다");
        }
        int cmp = x.getId().compareTo(y.getId());
        if (cmp == 0) {
            throw new IllegalArgumentException("같은 노드끼리는 이을 수 없습니다");
        }
        return cmp < 0 ? new PathEdge(x, y) : new PathEdge(y, x);
    }
}
