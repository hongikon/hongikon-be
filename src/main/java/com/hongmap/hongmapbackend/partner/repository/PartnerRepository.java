package com.hongmap.hongmapbackend.partner.repository;

import com.hongmap.hongmapbackend.partner.entity.Partner;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    // 목록/상세 응답은 affiliations까지 직렬화하므로 한 번의 쿼리로 함께 가져온다(N+1 방지).
    @Override
    @EntityGraph(attributePaths = "affiliations")
    List<Partner> findAll();

    @EntityGraph(attributePaths = "affiliations")
    List<Partner> findByCategory(String category);

    @EntityGraph(attributePaths = "affiliations")
    Optional<Partner> findWithAffiliationsById(Long id);

    // 필터 조건을 fetch join에 직접 걸면 컬렉션이 일치하는 소속 하나만 채워지므로,
    // 필터는 EXISTS 서브쿼리로 분리하고 affiliations는 전부 fetch한다.
    @Query("""
        SELECT DISTINCT p FROM Partner p
        LEFT JOIN FETCH p.affiliations
        WHERE EXISTS (
            SELECT 1 FROM PartnerAffiliation a
            WHERE a.partner = p AND a.affiliation = :affiliation
        )
        """)
    List<Partner> findByAffiliation(@Param("affiliation") String affiliation);

    @Query("""
        SELECT p FROM Partner p
        WHERE p.latitude BETWEEN :swLat AND :neLat
        AND p.longitude BETWEEN :swLng AND :neLng
        """)
    List<Partner> findWithinBounds(
        @Param("swLat") BigDecimal swLat,
        @Param("neLat") BigDecimal neLat,
        @Param("swLng") BigDecimal swLng,
        @Param("neLng") BigDecimal neLng
    );
}
