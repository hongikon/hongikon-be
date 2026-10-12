package com.hongmap.hongmapbackend.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long>, JpaSpecificationExecutor<News> {

    boolean existsBySourceUrl(String sourceUrl);

    /**
     * 크롤러 목록 한 페이지의 상세 링크 중 이미 저장된 것과 그 source_id. 글마다 exists 쿼리를 날리던 것을
     * 페이지당 한 번으로 줄이고, source_id가 빈 행만 골라 채울 수 있게 source_id도 같이 돌려준다.
     */
    @Query("SELECT n.sourceUrl AS sourceUrl, n.sourceId AS sourceId FROM News n WHERE n.sourceUrl IN :sourceUrls")
    List<SourceUrlState> findSourceUrlStates(@Param("sourceUrls") Collection<String> sourceUrls);

    interface SourceUrlState {
        String getSourceUrl();

        String getSourceId();
    }

    /** 상세 링크가 매번 바뀌는 게시판(건축학부 등)의 중복 판단용 — 게시판 출처+제목+작성일. */
    boolean existsBySourceIdAndTitleAndPublishedAt(String sourceId, String title, LocalDateTime publishedAt);

    /** 위와 같되 목록에서 작성일을 못 읽은 경우. */
    boolean existsBySourceIdAndTitle(String sourceId, String title);

    /**
     * 이미 저장된 글을 크롤러가 다시 만났을 때 source_id가 비어 있으면 채운다(자가 치유용).
     * 이미 값이 있는 행은 건드리지 않는다 — 먼저 기록된 게시판 출처를 유지한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE News n SET n.sourceId = :sourceId WHERE n.sourceUrl IN :sourceUrls AND n.sourceId IS NULL")
    int fillMissingSourceId(@Param("sourceId") String sourceId, @Param("sourceUrls") Collection<String> sourceUrls);

    /** 상단 고정 목록에 들어온 글을 켠다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE News n SET n.pinned = true WHERE n.sourceUrl IN :sourceUrls AND n.pinned = false")
    int pinBySourceUrls(@Param("sourceUrls") Collection<String> sourceUrls);

    /** 이 게시판(sourceId)에서 상단 고정 목록(keep)에 없는 글의 고정을 끈다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE News n SET n.pinned = false WHERE n.sourceId = :sourceId AND n.pinned = true AND n.sourceUrl NOT IN :keep")
    int unpinExcept(@Param("sourceId") String sourceId, @Param("keep") Collection<String> keep);

    /** 이 게시판(sourceId)에 상단 고정 글이 하나도 없을 때. 빈 목록으로 NOT IN을 만들 수 없어 따로 둔다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE News n SET n.pinned = false WHERE n.sourceId = :sourceId AND n.pinned = true")
    int unpinAll(@Param("sourceId") String sourceId);

    @Query("SELECT n FROM News n WHERE n.department IS NULL OR n.building IS NULL")
    List<News> findAllMissingLocation();
}
