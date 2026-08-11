package com.hongmap.hongmapbackend.bookmark;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    List<Bookmark> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Bookmark> findByUser_IdAndNews_Id(Long userId, Long newsId);

    boolean existsByUser_IdAndNews_Id(Long userId, Long newsId);
}
