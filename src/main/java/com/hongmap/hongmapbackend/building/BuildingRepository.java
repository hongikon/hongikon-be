package com.hongmap.hongmapbackend.building;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    Optional<Building> findByName(String name);

    Optional<Building> findByCode(String code);

    /** GET /map/data — sort_order, id 순. */
    List<Building> findAllByOrderBySortOrderAscIdAsc();
}
