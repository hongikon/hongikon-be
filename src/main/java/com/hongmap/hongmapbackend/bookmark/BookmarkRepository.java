package com.hongmap.hongmapbackend.bookmark;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    List<Bookmark> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Bookmark> findByUser_IdAndNews_Id(Long userId, Long newsId);

    /** 중복 행이 있어도 오류 없이 가장 먼저 만든 것 하나. */
    Optional<Bookmark> findFirstByUser_IdAndNews_IdOrderByIdAsc(Long userId, Long newsId);

    /** (user, news) 조합 전부 삭제. 지운 행 수. */
    @Modifying
    @Query("DELETE FROM Bookmark b WHERE b.user.id = :userId AND b.news.id = :newsId")
    int deleteAllByUserIdAndNewsId(@Param("userId") Long userId, @Param("newsId") Long newsId);

    boolean existsByUser_IdAndNews_Id(Long userId, Long newsId);

    void deleteByUser_Id(Long userId);
}
