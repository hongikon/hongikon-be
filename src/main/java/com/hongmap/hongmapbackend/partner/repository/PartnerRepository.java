package com.hongmap.hongmapbackend.partner.repository;

import com.hongmap.hongmapbackend.partner.entity.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    List<Partner> findByCategory(String category);

    @Query("""
        SELECT DISTINCT p FROM Partner p
        JOIN p.affiliations a
        WHERE a = :affiliation
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
