package com.hongmap.hongmapbackend.cafeteria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CafeteriaMenuRepository extends JpaRepository<CafeteriaMenu, Long> {

    List<CafeteriaMenu> findByMenuDateIn(Collection<LocalDate> dates);

    List<CafeteriaMenu> findByMenuDateBetween(LocalDate from, LocalDate to);

    boolean existsByMenuDateBetween(LocalDate from, LocalDate to);
}
