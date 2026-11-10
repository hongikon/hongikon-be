package com.hongmap.hongmapbackend.walkroute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WalkGraphVersionRepository extends JpaRepository<WalkGraphVersion, Long> {

    Optional<WalkGraphVersion> findFirstByStatusOrderByIdDesc(WalkGraphVersion.Status status);

    List<WalkGraphVersion> findAllByStatus(WalkGraphVersion.Status status);

    /** 목록용(본문 열은 읽지 않는다). */
    @Query("select new com.hongmap.hongmapbackend.walkroute.WalkGraphVersionRepository$Summary(v.id, v.status, v.sourceName, v.exportedAt, "
            + "v.vertexCount, v.edgeCount, v.createdAt, v.activatedAt) from WalkGraphVersion v order by v.id desc")
    List<Summary> findSummaries(org.springframework.data.domain.Pageable page);

    record Summary(Long id, WalkGraphVersion.Status status, String sourceName, String exportedAt, int vertexCount, int edgeCount,
                   java.time.LocalDateTime createdAt, java.time.LocalDateTime activatedAt) {
    }
}
